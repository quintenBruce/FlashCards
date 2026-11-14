CREATE TABLE IF NOT EXISTS documents (
  uid TEXT NOT NULL,
  kind TEXT NOT NULL,
  id TEXT NOT NULL,
  body TEXT NOT NULL,
  PRIMARY KEY (uid, kind, id)
);
