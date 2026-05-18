'use client';

// T6.6a (P24) — Browser-only map layer for `MapListHybridShell`.
//
// Loaded via `next/dynamic` from the shell so the entire react-leaflet
// import tree stays out of the SSR bundle. Renders the supplied pins as
// styled DivIcon markers and overlays the optional "you are here" disc.

import { useEffect } from 'react';
import { MapContainer, Marker, useMap } from 'react-leaflet';
import L from 'leaflet';
import { CachedTileLayer } from '@/components/map/CachedTileLayer';
import { TILE_URL, TILE_ATTRIBUTION } from '@/components/map/constants';
import '@/components/map/leaflet-setup';
import type { MapAnchor, MapPin } from './types';

interface MapListHybridMapLayerProps {
  pins: MapPin[];
  anchor?: MapAnchor;
  selectedPinId: string | null;
  onPinTap?: (id: string) => void;
  reduceMotion: boolean;
}

export default function MapListHybridMapLayer({
  pins,
  anchor,
  selectedPinId,
  onPinTap,
  reduceMotion,
}: MapListHybridMapLayerProps) {
  const fallbackCenter: [number, number] =
    anchor != null
      ? [anchor.latitude, anchor.longitude]
      : pins[0]
        ? [pins[0].latitude, pins[0].longitude]
        : [40.7484, -73.9857];

  return (
    <MapContainer
      center={fallbackCenter}
      zoom={15}
      style={{ height: '100%', width: '100%' }}
      scrollWheelZoom
      zoomControl={false}
      attributionControl={false}
    >
      <CachedTileLayer url={TILE_URL} attribution={TILE_ATTRIBUTION} />
      <RecenterOnAnchor anchor={anchor} />
      {pins.map((pin) => (
        <Marker
          key={pin.id}
          position={[pin.latitude, pin.longitude]}
          icon={pinDivIcon(pin, selectedPinId === pin.id, reduceMotion)}
          eventHandlers={{
            click: () => onPinTap?.(pin.id),
          }}
        />
      ))}
      {anchor && (
        <Marker
          position={[anchor.latitude, anchor.longitude]}
          icon={anchorDivIcon()}
          interactive={false}
        />
      )}
    </MapContainer>
  );
}

function RecenterOnAnchor({ anchor }: { anchor?: MapAnchor }) {
  const map = useMap();
  useEffect(() => {
    if (anchor) {
      map.flyTo([anchor.latitude, anchor.longitude], map.getZoom(), { duration: 0.6 });
    }
  }, [anchor, map]);
  return null;
}

function pinDivIcon(pin: MapPin, isActive: boolean, reduceMotion: boolean): L.DivIcon {
  // Two-layer pulse halo on the active selection. Pulse keyframes use a
  // global animation defined in `globals.css`; under reduce-motion we
  // swap for a thin static ring.
  const animateClass = isActive && !reduceMotion ? 'mlhPinPulse' : '';
  const ringStyle =
    pin.state === 'pending'
      ? `outline: 2px dashed ${pin.color}; outline-offset: 2px;`
      : `border: 2px solid #fff;`;
  const haloHtml = isActive
    ? `
      <span class="mlhPinHaloOuter ${animateClass}" style="background:${pin.color};"></span>
      <span class="mlhPinHaloInner ${animateClass}" style="background:${pin.color};"></span>
    `
    : '';
  return L.divIcon({
    className: 'mlhPin',
    html: `
      <div class="mlhPinRoot" data-pin-id="${pin.id}">
        ${haloHtml}
        <span class="mlhPinDot" style="background:${pin.color}; ${ringStyle}"></span>
      </div>
    `,
    iconSize: [50, 50],
    iconAnchor: [25, 25],
  });
}

function anchorDivIcon(): L.DivIcon {
  return L.divIcon({
    className: 'mlhAnchor',
    html: `
      <div class="mlhAnchorRoot" aria-label="You are here">
        <span class="mlhAnchorHalo"></span>
        <span class="mlhAnchorDot"></span>
      </div>
    `,
    iconSize: [28, 28],
    iconAnchor: [14, 14],
  });
}
