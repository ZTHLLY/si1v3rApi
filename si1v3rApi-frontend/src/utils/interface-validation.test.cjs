const { test } = require('node:test');
const assert = require('node:assert/strict');
const ts = require('typescript');
const fs = require('node:fs');
const code = ts.transpileModule(fs.readFileSync(__dirname + '/interface-validation.ts', 'utf8'), {
  compilerOptions: { module: ts.ModuleKind.CommonJS },
}).outputText;
const validation = { exports: {} };
new Function('exports', code)(validation.exports);
const { isValidApiPath, isValidApiMethod } = validation.exports;
test('accepts canonical API paths without normalization', () => {
  for (const path of ['/api/', '/api/user', '/api/a-b_1', '/api/a%20b']) assert.equal(isValidApiPath(path), true, path);
});
test('rejects all backend forbidden path forms', () => {
  for (const path of ['', '/api', '/user', 'https://x/api/user', '/api//user', '/api/./user', '/api/../user', '/api/.', '/api/..', '/api/a;b', '/api/a\\b', '/api/%2E', '/api/%2f', '/api/%5C', '/api/%00', '/api/a?x=1', '/api/a#b', '/api/a b', '/api/%zz', '/api/[x]', undefined]) assert.equal(isValidApiPath(path), false, String(path));
});
test('accepts only supported methods case-insensitively like backend', () => {
  for (const method of ['GET', 'POST', 'PUT', 'DELETE', 'post']) assert.equal(isValidApiMethod(method), true);
  for (const method of ['PATCH', 'HEAD', '', ' GET', undefined]) assert.equal(isValidApiMethod(method), false);
});
