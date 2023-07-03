package com.example.springkotlinreactiveweb

import org.springframework.boot.fromApplication
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.boot.with
import org.springframework.context.annotation.Bean
import org.testcontainers.containers.PostgreSQLContainer

@TestConfiguration(proxyBeanMethods = false)
class TestSpringKotlinReactiveWebApplication {

	@Bean
	@ServiceConnection
	fun postgresContainer(): PostgreSQLContainer<*> {
		return PostgreSQLContainer("postgres:latest")
	}

}

fun main(args: Array<String>) {
	fromApplication<SpringKotlinReactiveWebApplication>().with(TestSpringKotlinReactiveWebApplication::class).run(*args)
}
