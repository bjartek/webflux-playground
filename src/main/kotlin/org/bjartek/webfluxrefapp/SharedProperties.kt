package org.bjartek.webfluxrefapp

import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.PropertySource
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.core.io.support.DefaultPropertySourceFactory
import org.springframework.core.io.support.EncodedResource

/**
 * If you want to share some common properties in a starter this is a way to do it with reading them
 * from a yaml file
 */
@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE)
@PropertySource("classpath:baseproperties.yml", factory = YamlPropertyLoaderFactory::class)
class SharedProperties

class YamlPropertyLoaderFactory : DefaultPropertySourceFactory() {
    override fun createPropertySource(
        name: String?,
        resource: EncodedResource
    ): org.springframework.core.env.PropertySource<*> {
        return YamlPropertySourceLoader().load(resource.resource.filename, resource.resource).first()
    }
}
