# Artifact: jdbcPool

Fragments:
- `metadata.xml`: repository metadata around the artifact
- `jdbc-connection.xml`: JDBC connection configuration
- `input.xml`: when using a jdbcPool as service
- `output.xml`: when using a jdbcPool as service

Every jdbcPool is also an executable service where you can run arbitrary queries against its database.
This should NEVER be used in code but can be useful for debugging or raw manipulation.
Check the `input.xml` fragment of the jdbcPool for the required input shape.
Only pass a `resultType` to the input if you want the output bound to a specific structure definition. Otherwise leave it empty.

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

Managed models:
- all types in the model are automatically synced to the database tables using only non-destructive changes

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
