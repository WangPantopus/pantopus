const supabaseAdmin = require('../../config/supabaseAdmin');
const { resetTables, setAuthMocks, getTable } = require('../__mocks__/supabaseAdmin');

jest.mock('../../config/auth', () => ({
  signUp: jest.fn(),
  signIn: jest.fn(),
}));

const router = require('../../routes/users');

function findHandler(method, path) {
  for (const layer of router.stack) {
    if (
      layer.route &&
      layer.route.path === path &&
      layer.route.methods[method.toLowerCase()]
    ) {
      const stack = layer.route.stack;
      return stack[stack.length - 1].handle;
    }
  }
  throw new Error(`Handler not found: ${method} ${path}`);
}

function mockReq({ body = {}, headers = {} } = {}) {
  const normalizedHeaders = Object.fromEntries(
    Object.entries(headers).map(([k, v]) => [String(k).toLowerCase(), v])
  );

  return {
    body,
    method: 'POST',
    path: '/register',
    originalUrl: '/api/users/register',
    ip: '127.0.0.1',
    get: (headerName) => {
      const key = String(headerName || '').toLowerCase();
      return normalizedHeaders[key] || (key === 'user-agent' ? 'test-agent' : undefined);
    },
  };
}

function mockRes() {
  return {
    _status: 200,
    _json: null,
    status(code) {
      this._status = code;
      return this;
    },
    json(payload) {
      this._json = payload;
      return this;
    },
  };
}

const registerHandler = findHandler('POST', '/register');

describe('POST /register', () => {
  beforeEach(() => {
    resetTables();
    jest.clearAllMocks();
    process.env.NODE_ENV = 'test';
    process.env.AUTH_REDIRECT_URL = 'https://pantopus.com';
  });

  test('returns 400 when user profile insert loses a username uniqueness race', async () => {
    setAuthMocks({
      signUp: jest.fn().mockResolvedValue({
        data: {
          user: {
            id: 'auth-user-race',
            email: 'race@example.com',
            email_confirmed_at: null,
          },
        },
        error: null,
      }),
    });

    const originalFrom = supabaseAdmin.from.bind(supabaseAdmin);
    jest.spyOn(supabaseAdmin, 'from').mockImplementation((tableName) => {
      const builder = originalFrom(tableName);
      if (tableName !== 'User') return builder;

      const originalInsert = builder.insert.bind(builder);
      builder.insert = (payload) => {
        if (payload?.id !== 'auth-user-race') {
          return originalInsert(payload);
        }

        return {
          select() {
            return this;
          },
          async single() {
            return {
              data: null,
              error: {
                message: 'duplicate key value violates unique constraint "User_username_key"',
                code: '23505',
                details: 'Key (username)=(masontest12482134124) already exists.',
                hint: null,
              },
            };
          },
        };
      };

      return builder;
    });

    const req = mockReq({
      body: {
        email: 'race@example.com',
        password: 'test12312345',
        username: 'masontest12482134124',
        firstName: 'Mason',
        lastName: 'Turner',
      },
      headers: {
        origin: 'https://pantopus.com',
      },
    });
    const res = mockRes();

    await registerHandler(req, res);

    expect(res._status).toBe(400);
    expect(res._json).toEqual({ error: 'Username already taken' });
    expect(supabaseAdmin.auth.admin.deleteUser).toHaveBeenCalledWith('auth-user-race');
    expect(getTable('User')).toHaveLength(0);
  });
});
