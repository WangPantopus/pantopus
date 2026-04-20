/** @type {import('jest').Config} */
module.exports = {
  testEnvironment: 'node',
  testMatch: ['**/tests/**/*.test.js'],
  testPathIgnorePatterns: ['/node_modules/', '/tests/integration/(?!auth-exploits)'],
  // Map requires to mocks so tests don't need live Supabase/Stripe
  moduleNameMapper: {
    '^../config/supabaseAdmin$': '<rootDir>/tests/__mocks__/supabaseAdmin.js',
    '^../config/supabase$': '<rootDir>/tests/__mocks__/supabaseAdmin.js',
    '^../utils/logger$': '<rootDir>/tests/__mocks__/logger.js',
    '^\\.\/logger$': '<rootDir>/tests/__mocks__/logger.js',
    '^../../config/supabaseAdmin$': '<rootDir>/tests/__mocks__/supabaseAdmin.js',
    '^../../utils/logger$': '<rootDir>/tests/__mocks__/logger.js',
    '^../services/notificationService$': '<rootDir>/tests/__mocks__/notificationService.js',
    '^../../services/notificationService$': '<rootDir>/tests/__mocks__/notificationService.js',
    '^../notificationService$': '<rootDir>/tests/__mocks__/notificationService.js',
    '^\\.\/notificationService$': '<rootDir>/tests/__mocks__/notificationService.js',
    '^../services/pushService$': '<rootDir>/tests/__mocks__/pushService.js',
    '^\\.\/pushService$': '<rootDir>/tests/__mocks__/pushService.js',
    '^dotenv$': '<rootDir>/tests/__mocks__/dotenv.js',
    '^../middleware/verifyToken$': '<rootDir>/tests/__mocks__/verifyToken.js',
  },
  // Automatically clear mock calls and instances between every test
  clearMocks: true,
};
