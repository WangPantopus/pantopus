// ============================================================
// TEST: local US state resolution (utils/usState)
//
// This exists so the anonymous Unlisted route can keep its promise —
// "we do not send it anywhere else" — while still answering. The whole
// point is that no network call happens, so the first assertion here is
// that nothing is fetched, and the rest are about being WRONG SAFELY:
// a mis-resolved state shows someone the wrong state's confidentiality
// program, which on this page is the harm the feature exists to avoid.
// Ambiguous input must resolve to null, not to a guess.
// ============================================================

const { resolveUsState } = require('../utils/usState');

describe('the resolver never reaches the network', () => {
  test('a well-formed US address resolves with zero fetch calls', () => {
    const realFetch = global.fetch;
    global.fetch = jest.fn();
    try {
      expect(resolveUsState('1421 SE Oak St, Portland, OR 97214')).toBe('OR');
      expect(global.fetch).not.toHaveBeenCalled();
    } finally {
      global.fetch = realFetch;
    }
  });
});

describe('an explicit state always wins', () => {
  test('the trailing two-letter code, with and without a ZIP', () => {
    expect(resolveUsState('1421 SE Oak St, Portland, OR 97214')).toBe('OR');
    expect(resolveUsState('1421 SE Oak St, Portland, OR')).toBe('OR');
    expect(resolveUsState('742 Evergreen Terrace, Springfield, IL 62704')).toBe('IL');
  });

  test('a full state name anywhere in the string', () => {
    expect(resolveUsState('1421 SE Oak Street, Portland, Oregon')).toBe('OR');
    expect(resolveUsState('100 Main St, Charleston, West Virginia 25301')).toBe('WV');
  });

  test('the longest name matches first — West Virginia is not Virginia', () => {
    expect(resolveUsState('100 Main St, Charleston, West Virginia')).toBe('WV');
    expect(resolveUsState('1 Elm Ave, Richmond, Virginia')).toBe('VA');
    // And New York is not a street called York.
    expect(resolveUsState('55 Water St, New York, NY')).toBe('NY');
  });

  test('a city named after another state does not win', () => {
    // The trailing slot is where a US address puts its state, so the code
    // there beats a state name sitting in the city.
    expect(resolveUsState('4600 Main St, Kansas City, MO 64112')).toBe('MO');
    expect(resolveUsState('4600 Main St, Kansas City, Missouri')).toBe('MO');
    expect(resolveUsState('1 Philadelphia St, Indiana, PA 15701')).toBe('PA');
    expect(resolveUsState('1 Philadelphia St, Indiana, Pennsylvania')).toBe('PA');
    // And the city that IS in its namesake state still resolves to it.
    expect(resolveUsState('4600 Main St, Kansas City, Kansas')).toBe('KS');
  });

  test('a stated state beats a ZIP that disagrees with it', () => {
    // Someone who wrote the state gets the state they wrote. A transposed
    // ZIP digit must not silently relocate them.
    expect(resolveUsState('1421 SE Oak St, Portland, OR 97214')).toBe('OR');
    expect(resolveUsState('1421 SE Oak St, Portland, OR 12345')).toBe('OR');
  });
});

describe('a ZIP alone is enough', () => {
  test('common prefixes land in the right state', () => {
    expect(resolveUsState('97214')).toBe('OR');
    expect(resolveUsState('10001')).toBe('NY');
    expect(resolveUsState('90210')).toBe('CA');
    expect(resolveUsState('33101')).toBe('FL');
    expect(resolveUsState('99501')).toBe('AK');
    expect(resolveUsState('96801')).toBe('HI');
  });

  test('the DC / Virginia / Maryland split, which is the easiest to get wrong', () => {
    expect(resolveUsState('20001')).toBe('DC');
    expect(resolveUsState('20101')).toBe('VA'); // 201 is Dulles, not DC
    expect(resolveUsState('20601')).toBe('MD');
  });

  test('a ZIP embedded in a fuller address still resolves', () => {
    expect(resolveUsState('1421 Oak St 97214')).toBe('OR');
  });
});

describe('being wrong safely', () => {
  test('nothing recognisable resolves to null, never to a guess', () => {
    expect(resolveUsState('')).toBeNull();
    expect(resolveUsState(null)).toBeNull();
    expect(resolveUsState('   ')).toBeNull();
    expect(resolveUsState('somewhere nice')).toBeNull();
    expect(resolveUsState('10 Downing Street, London')).toBeNull();
  });

  test('two-letter English words in a street name are not read as states', () => {
    // "IN", "OR", "ME", "HI", "OK", "DE", "LA" are all ordinary words. A
    // naive whole-string scan resolved these; the tail rule must not.
    expect(resolveUsState('12 Lincoln In The Park')).toBeNull();
    expect(resolveUsState('5 Hi Line Road')).toBeNull();
    expect(resolveUsState('9 Or Else Lane')).toBeNull();
  });

  test('territories and military mail resolve to null, not to a neighbour', () => {
    // Puerto Rico, the Virgin Islands, Guam and APO/FPO are not states and
    // have no entry in the ACP registry. Returning the numerically nearby
    // state would show someone a program they cannot apply to.
    expect(resolveUsState('00901')).toBeNull(); // PR
    expect(resolveUsState('00802')).toBeNull(); // VI
    expect(resolveUsState('96910')).toBeNull(); // GU
    expect(resolveUsState('34001')).toBeNull(); // AA military
    expect(resolveUsState('09001')).toBeNull(); // AE military
  });

  test('an unassigned ZIP prefix resolves to null', () => {
    expect(resolveUsState('26900')).toBeNull(); // 269 is unassigned
    expect(resolveUsState('42900')).toBeNull(); // 429 is unassigned
  });
});
