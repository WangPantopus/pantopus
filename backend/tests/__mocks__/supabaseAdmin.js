// ============================================================
// MOCK: supabaseAdmin
// In-memory mock of supabase admin client for tests.
// Stores data in plain objects; supports chained query builders.
// ============================================================

const tables = {};
let _rpcMock = null;
let _authMocks = {
  signUp: async () => ({
    data: {
      user: {
        id: 'mock-auth-user-id',
        email: 'mock@example.com',
        email_confirmed_at: null,
        user_metadata: {},
      },
    },
    error: null,
  }),
  signInWithPassword: async () => ({
    data: {
      user: {
        id: 'mock-user-id',
        email: 'mock@example.com',
      },
      session: {
        access_token: 'mock-access-token',
        refresh_token: 'mock-refresh-token',
      },
    },
    error: null,
  }),
  resend: async () => ({ data: {}, error: null }),
  verifyOtp: async () => ({ data: { user: null, session: null }, error: null }),
  resetPasswordForEmail: async () => ({ data: {}, error: null }),
  signInWithOAuth: async () => ({ data: { url: 'https://example.com/oauth' }, error: null }),
  exchangeCodeForSession: async () => ({
    data: {
      session: {
        access_token: 'mock-access-token',
        refresh_token: 'mock-refresh-token',
        expires_in: 3600,
        expires_at: Math.floor(Date.now() / 1000) + 3600,
      },
      user: {
        id: 'mock-user-id',
        email: 'mock@example.com',
        user_metadata: {},
      },
    },
    error: null,
  }),
  getUser: async () => ({
    data: {
      user: {
        id: 'mock-user-id',
        email: 'mock@example.com',
        user_metadata: {},
      },
    },
    error: null,
  }),
};

function resetTables() {
  Object.keys(tables).forEach((k) => delete tables[k]);
  _rpcMock = null;
  _authMocks = {
    signUp: async () => ({
      data: {
        user: {
          id: 'mock-auth-user-id',
          email: 'mock@example.com',
          email_confirmed_at: null,
          user_metadata: {},
        },
      },
      error: null,
    }),
    signInWithPassword: async () => ({
      data: {
        user: {
          id: 'mock-user-id',
          email: 'mock@example.com',
        },
        session: {
          access_token: 'mock-access-token',
          refresh_token: 'mock-refresh-token',
        },
      },
      error: null,
    }),
    resend: async () => ({ data: {}, error: null }),
    verifyOtp: async () => ({ data: { user: null, session: null }, error: null }),
    resetPasswordForEmail: async () => ({ data: {}, error: null }),
    signInWithOAuth: async () => ({ data: { url: 'https://example.com/oauth' }, error: null }),
    exchangeCodeForSession: async () => ({
      data: {
        session: {
          access_token: 'mock-access-token',
          refresh_token: 'mock-refresh-token',
          expires_in: 3600,
          expires_at: Math.floor(Date.now() / 1000) + 3600,
        },
        user: {
          id: 'mock-user-id',
          email: 'mock@example.com',
          user_metadata: {},
        },
      },
      error: null,
    }),
    getUser: async () => ({
      data: {
        user: {
          id: 'mock-user-id',
          email: 'mock@example.com',
          user_metadata: {},
        },
      },
      error: null,
    }),
  };
}

function getTable(name) {
  if (!tables[name]) tables[name] = [];
  return tables[name];
}

function seedTable(name, rows) {
  tables[name] = [...rows];
}

function setRpcMock(fn) {
  _rpcMock = fn;
}
function resetRpc() {
  _rpcMock = null;
}
function setAuthMocks(overrides = {}) {
  _authMocks = { ..._authMocks, ...overrides };
}

/**
 * Chainable query builder that mimics supabaseAdmin.from(table)
 */
function createQueryBuilder(tableName) {
  let rows = () => getTable(tableName);
  let filters = [];
  let selectFields = '*';
  let updatePayload = null;
  let insertPayload = null;
  let isSingle = false;
  let isUpsert = false;
  let isCountMode = false;
  let isHeadMode = false;
  let rangeStart = null;
  let rangeEnd = null;

  const builder = {
    select(fields = '*', options) {
      selectFields = fields;
      if (options?.count) isCountMode = true;
      if (options?.head) isHeadMode = true;
      return builder;
    },
    eq(field, value) {
      filters.push((row) => row[field] === value);
      return builder;
    },
    neq(field, value) {
      filters.push((row) => row[field] !== value);
      return builder;
    },
    in(field, values) {
      filters.push((row) => values.includes(row[field]));
      return builder;
    },
    not(field, operator, value) {
      if (operator === 'in') {
        // value like '("a","b")' — parse it
        const vals = value
          .replace(/[()]/g, '')
          .split(',')
          .map((s) => s.replace(/"/g, '').trim());
        filters.push((row) => !vals.includes(row[field]));
      } else if (operator === 'eq') {
        filters.push((row) => row[field] !== value);
      } else if (operator === 'is') {
        filters.push((row) => row[field] !== value);
      }
      return builder;
    },
    lte(field, value) {
      filters.push((row) => row[field] <= value);
      return builder;
    },
    gte(field, value) {
      filters.push((row) => row[field] >= value);
      return builder;
    },
    lt(field, value) {
      filters.push((row) => row[field] < value);
      return builder;
    },
    gt(field, value) {
      filters.push((row) => row[field] > value);
      return builder;
    },
    is(field, value) {
      filters.push((row) => row[field] === value);
      return builder;
    },
    contains(field, values) {
      // Supabase .contains() — array column must include ALL of the given values
      filters.push((row) => {
        const col = row[field];
        if (!Array.isArray(col)) return false;
        return values.every((v) => col.includes(v));
      });
      return builder;
    },
    overlaps(field, values) {
      // Supabase .overlaps() — array column shares at least one element
      filters.push((row) => {
        const col = row[field];
        if (!Array.isArray(col)) return false;
        return values.some((v) => col.includes(v));
      });
      return builder;
    },
    or(filterString) {
      // Parse Supabase .or() filter strings.
      // Supports patterns like:
      //   "and(requester_id.eq.a,addressee_id.eq.b),and(requester_id.eq.b,addressee_id.eq.a)"
      //   "field.eq.value,field.eq.value"
      const parseCondition = (cond) => {
        // Match field.operator.value patterns
        const match = cond.match(/^(\w+)\.(eq|neq|gt|gte|lt|lte|is)\.(.+)$/);
        if (!match) return () => true;
        const [, field, op, val] = match;
        // Coerce value types
        let value = val;
        if (value === 'null') value = null;
        else if (value === 'true') value = true;
        else if (value === 'false') value = false;
        switch (op) {
          case 'eq':
            return (row) => row[field] === value;
          case 'neq':
            return (row) => row[field] !== value;
          case 'gt':
            return (row) => row[field] > value;
          case 'gte':
            return (row) => row[field] >= value;
          case 'lt':
            return (row) => row[field] < value;
          case 'lte':
            return (row) => row[field] <= value;
          case 'is':
            return (row) => row[field] === value;
          default:
            return () => true;
        }
      };

      // Split into top-level groups (either "and(...)" blocks or bare conditions)
      const groups = [];
      let depth = 0;
      let current = '';
      for (let i = 0; i < filterString.length; i++) {
        const ch = filterString[i];
        if (ch === '(') {
          depth++;
          current += ch;
        } else if (ch === ')') {
          depth--;
          current += ch;
        } else if (ch === ',' && depth === 0) {
          groups.push(current.trim());
          current = '';
        } else {
          current += ch;
        }
      }
      if (current.trim()) groups.push(current.trim());

      const groupFns = groups.map((g) => {
        const andMatch = g.match(/^and\((.+)\)$/);
        if (andMatch) {
          // Inner conditions are comma-separated inside and(...)
          const innerParts = andMatch[1].split(',').map((s) => s.trim());
          const fns = innerParts.map(parseCondition);
          return (row) => fns.every((fn) => fn(row));
        }
        // Bare condition
        return parseCondition(g);
      });

      filters.push((row) => groupFns.some((fn) => fn(row)));
      return builder;
    },
    textSearch(_field, _query, _opts) {
      // No-op in mock — accept all rows (text search is integration-level)
      return builder;
    },
    filter(field, operator, value) {
      if (operator === 'is') {
        filters.push((row) => row[field] === (value === 'null' ? null : value));
      }
      return builder;
    },
    order(field, opts = {}) {
      return builder;
    },
    limit(n) {
      return builder;
    },
    range(from, to) {
      rangeStart = from;
      rangeEnd = to;
      return builder;
    },
    single() {
      isSingle = true;
      return builder;
    },
    maybeSingle() {
      isSingle = true;
      return builder;
    },
    // INSERT
    insert(payload) {
      insertPayload = Array.isArray(payload) ? payload : [payload];
      return builder;
    },
    upsert(payload) {
      insertPayload = Array.isArray(payload) ? payload : [payload];
      isUpsert = true;
      return builder;
    },
    // UPDATE
    update(payload) {
      updatePayload = payload;
      return builder;
    },
    // DELETE
    delete() {
      updatePayload = '__DELETE__';
      return builder;
    },
    // Execute — returns a real Promise so .then().catch() chains work
    then(resolve, reject) {
      return new Promise((res) => {
        try {
          res(builder._execute());
        } catch (err) {
          res({ data: null, error: { message: err.message } });
        }
      }).then(resolve, reject);
    },
    catch(handler) {
      return builder.then(undefined, handler);
    },
    _execute() {
      const table = getTable(tableName);

      // INSERT
      if (insertPayload) {
        const inserted = insertPayload.map((row) => {
          if (!row.id)
            row.id = `mock-${tableName.toLowerCase()}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
          if (isUpsert) {
            const idx = table.findIndex((r) => r.id === row.id);
            if (idx >= 0) {
              table[idx] = { ...table[idx], ...row };
              return table[idx];
            }
          }
          table.push({ ...row });
          return row;
        });
        return { data: isSingle ? inserted[0] : inserted, error: null };
      }

      // FILTER
      let matched = table.filter((row) => filters.every((fn) => fn(row)));
      const matchedCount = matched.length;

      if (rangeStart != null || rangeEnd != null) {
        const start = Math.max(0, rangeStart || 0);
        const endExclusive = rangeEnd == null ? matched.length : rangeEnd + 1;
        matched = matched.slice(start, endExclusive);
      }

      // UPDATE
      if (updatePayload && updatePayload !== '__DELETE__') {
        matched.forEach((row) => {
          const idx = table.indexOf(row);
          table[idx] = { ...row, ...updatePayload };
        });
        const updated = matched.map((row) => {
          const idx = table.findIndex((r) => r.id === row.id);
          return table[idx];
        });
        return { data: isSingle ? updated[0] || null : updated, error: null };
      }

      // DELETE
      if (updatePayload === '__DELETE__') {
        matched.forEach((row) => {
          const idx = table.indexOf(row);
          if (idx >= 0) table.splice(idx, 1);
        });
        return { data: matched, error: null };
      }

      // SELECT
      let result;
      if (isSingle) {
        result = { data: matched[0] || null, error: null };
      } else {
        result = { data: isHeadMode ? null : matched, error: null };
      }
      if (isCountMode) {
        result.count = matchedCount;
      }
      return result;
    },
  };

  return builder;
}

const supabaseAdmin = {
  from: (tableName) => createQueryBuilder(tableName),
  rpc: async (...args) => {
    if (_rpcMock) return _rpcMock(...args);
    return { data: null, error: { message: 'No RPC mock configured' } };
  },
  auth: {
    signUp: (...args) => _authMocks.signUp(...args),
    signInWithPassword: (...args) => _authMocks.signInWithPassword(...args),
    resend: (...args) => _authMocks.resend(...args),
    verifyOtp: (...args) => _authMocks.verifyOtp(...args),
    resetPasswordForEmail: (...args) => _authMocks.resetPasswordForEmail(...args),
    signInWithOAuth: (...args) => _authMocks.signInWithOAuth(...args),
    exchangeCodeForSession: (...args) => _authMocks.exchangeCodeForSession(...args),
    getUser: (...args) => _authMocks.getUser(...args),
    admin: {
      deleteUser: jest.fn().mockResolvedValue({ data: {}, error: null }),
    },
  },
};

module.exports = supabaseAdmin;
module.exports.resetTables = resetTables;
module.exports.seedTable = seedTable;
module.exports.getTable = getTable;
module.exports.setRpcMock = setRpcMock;
module.exports.resetRpc = resetRpc;
module.exports.setAuthMocks = setAuthMocks;
