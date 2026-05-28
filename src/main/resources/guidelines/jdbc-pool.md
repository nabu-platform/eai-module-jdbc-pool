# Artifact: jdbcPool

Fragments:
- `metadata.xml`: repository metadata around the artifact
- `jdbc-connection.xml`: JDBC connection configuration

## Fragment: jdbc-connection.xml

Use `jdbc-connection.xml` to read and update the JDBC pool configuration.

Fields:
- `poolProxy`: optional JDBC pool artifact to proxy instead of configuring a direct connection. Must resolve to another jdbcPool artifact
- `driverClassName`: JDBC driver class.
- `jdbcUrl`: JDBC connection URL.
- `username`: username used for the JDBC connection.
- `password`: password used for the JDBC connection.
- `context`: optional context string for contextual resolution.
- `connectionTimeout`: maximum time in milliseconds to wait for a connection.
- `idleTimeout`: maximum idle time in milliseconds.
- `maximumPoolSize`: maximum number of pooled connections.
- `minimumIdle`: minimum number of idle connections.
- `autoCommit`: whether auto-commit is enabled. Autocommit should almost always be turned off.
- `maxLifetime`: maximum lifetime in milliseconds for pooled connections.
- `dialect`: SQL dialect implementation class.
- `defaultLanguage`: default translation language.
- `translationGet`: translation read service. Required interface: `be.nabu.libs.services.jdbc.api.JDBCTranslator.get`.
- `translationSet`: translation write service. Required interface: `be.nabu.libs.services.jdbc.api.JDBCTranslator.set`.
- `translationGetBinding`: translation binding lookup service. Required interface: `be.nabu.libs.services.jdbc.api.JDBCTranslator.getBinding`.
- `translationMapLanguage`: translation language mapping service. Required interface: `be.nabu.libs.services.jdbc.api.JDBCTranslator.mapLanguage`.
- `affixes`: optional datasource affix mappings.
- `managedModels`: optional managed model registries.
- `managedTypes`: optional explicitly managed types.

Password handling:
- The returned `jdbc-connection.xml` never includes the current password.
- To keep the existing password unchanged, omit the `password` field entirely from the incoming fragment.
- To replace the password, include a `password` field with the new cleartext value in the incoming fragment.
- Sending an empty `password` element clears the stored password.

Driver handling:
- `driverClassName` must be the fully qualified Java class name of a `java.sql.Driver` implementation.

Dialect handling:
- `dialect` must be the fully qualified Java class name of a `be.nabu.libs.services.jdbc.api.SQLDialect` implementation.

## Currently supported drivers

{{KNOWN_DRIVERS}}

## Currently supported dialects

{{KNOWN_DIALECTS}}
