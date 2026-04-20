import RootSessionBootstrap from '@/components/RootSessionBootstrap';

// ─────────────────────────────────────────────────────────────────────────────
// Pantopus Homepage — Blueprint v3 Redesign (15-section flow)
// Thin orchestrator — all sections extracted to _components/
// ─────────────────────────────────────────────────────────────────────────────

import NavBar from './_components/NavBar';
import HeroSection from './_components/HeroSection';
import TrustBandSection from './_components/TrustBandSection';
import FirstWinSection from './_components/FirstWinSection';
import AIRealitySection from './_components/AIRealitySection';
import AICreationSection from './_components/AICreationSection';
import MarketplaceSection from './_components/MarketplaceSection';
import PulseFeedSection from './_components/PulseFeedSection';
import MapSection from './_components/MapSection';
// import TestimonialsSection from './_components/TestimonialsSection'; // Uncomment after Prompt 4 creates this component
import HouseholdSection from './_components/HouseholdSection';
import FeaturesSection from './_components/FeaturesSection';
import HowItWorksSection from './_components/HowItWorksSection';
import TrustSafetySection from './_components/TrustSafetySection';
import FinalCTASection from './_components/FinalCTASection';
import FooterSection from './_components/FooterSection';

export default function HomePage() {
  return (
    <div className="min-h-screen bg-app-surface overflow-x-hidden">
      <RootSessionBootstrap />

      {/* §1 — Navigation */}
      <NavBar />

      {/* §2 — Hero */}
      <HeroSection />

      {/* §3 — Trust Band */}
      <TrustBandSection />

      {/* §4 — First Win Cards */}
      <FirstWinSection />

      {/* §5 — The AI Reality (emotional pivot) */}
      <AIRealitySection />

      {/* §6 — AI-Powered Creation */}
      <AICreationSection />

      {/* §7 — Marketplace Spotlight */}
      <MarketplaceSection />

      {/* §8 — The Pulse: Feed */}
      <PulseFeedSection />

      {/* §9 — The Map */}
      <MapSection />

      {/* §11 — Household (Home + Mailbox) */}
      <HouseholdSection />

      {/* §12 — Features Grid */}
      <FeaturesSection />

      {/* §13 — How It Works */}
      <HowItWorksSection />

      {/* §14 — Trust & Verification */}
      <TrustSafetySection />

      {/* §15 — Final CTA */}
      <FinalCTASection />

      {/* Footer */}
      <FooterSection />
    </div>
  );
}
