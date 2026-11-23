// Runs once on first start (empty volume), authenticated as the root user.
// Creates the backend's application user with readWrite on the logs database only.
const appDbName = process.env.MONGO_APP_DB || 'crm104_logs';
const appUser = process.env.MONGO_APP_USER;
const appPassword = process.env.MONGO_APP_PASSWORD;

if (!appUser || !appPassword) {
  throw new Error('MONGO_APP_USER and MONGO_APP_PASSWORD must be set');
}

const appDb = db.getSiblingDB(appDbName);
appDb.createUser({
  user: appUser,
  pwd: appPassword,
  roles: [{ role: 'readWrite', db: appDbName }],
});
print(`Created MongoDB user '${appUser}' with readWrite on '${appDbName}'`);
