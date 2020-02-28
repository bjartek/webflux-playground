# Playground for webflux

THis is a playground to play around with spring reactor and various concepts and how to translate them into the reactive world. 

 - Logging with MDC fields
 - Access logging
 - Lading properties from a yaml file (will be used in a spring boot starter)
 - Exposing prometheus metrics for health and for individual health indicators
 - Spring security authentication using Bearer token. 
 
## Issues

 - When I add spring-security-starter to classpath MDC fields are gone from access log
 - User MDC field is not propagated propperly