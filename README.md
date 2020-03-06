# Playground for webflux

This is a playground to play around with spring reactor and various concepts and how to translate them into the reactive world. 

 - Logging should include MDC/Security information in coroutine and reactor logging
 - Spring security to get a user from Bearer token
 - Exposing a health metric with the overall status of health checks
 - Exposing a health_indicator for each individual health check
 - Access logging WebFilter since Nettys access log does not pick up MDC fields
 - Loading a yaml file in a starter to enabled shared properties for a corporation


