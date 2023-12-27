package com.example.springkotlinreactiveweb

import kotlinx.coroutines.flow.count
import kotlinx.coroutines.runBlocking
import org.reactivestreams.Publisher
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.data.annotation.Id
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.http.server.reactive.ServerHttpResponse
import org.springframework.http.server.reactive.ServerHttpResponseDecorator
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.*
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.io.ByteArrayOutputStream
import java.nio.channels.Channels

@SpringBootApplication
class SpringKotlinReactiveWebApplication {

    @Bean
    fun init(customerRepository: CustomerRepository): CommandLineRunner {
        return CommandLineRunner {
            runBlocking {
                println(customerRepository.findAll().count())
            }
        }
    }

    @Bean
    fun http(customerRepository: CustomerRepository) = coRouter {
        GET("/customers") {
            ServerResponse.ok().bodyAndAwait(customerRepository.findAll())
        }
        GET("/customers/{id}") {
            val id = it.pathVariable("id").toInt();
            val result = customerRepository.findById(id)
            if (result != null) {
                ServerResponse.ok().bodyValueAndAwait(result)
            } else {
                ServerResponse.notFound().buildAndAwait()
            }
        }
    }
}

fun main(args: Array<String>) {
    runApplication<SpringKotlinReactiveWebApplication>(*args)
}

interface CustomerRepository : CoroutineCrudRepository<Customer, Int>

data class Customer(@Id val id: Int?, val name: String)

@Component
class LoggingWebFilter : WebFilter {
    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain) =
        chain.filter(
            exchange.mutate().response(LoggingResponseDecorator(exchange.response)).build()
        )
}

class LoggingResponseDecorator internal constructor(delegate: ServerHttpResponse) :
    ServerHttpResponseDecorator(delegate) {

    override fun writeWith(body: Publisher<out DataBuffer>): Mono<Void> {
        return super.writeWith(
            Flux.from(body)
                .doOnNext { buffer: DataBuffer ->
                    val bodyStream = ByteArrayOutputStream()
                    Channels.newChannel(bodyStream)
                        .write(buffer.asByteBuffer().asReadOnlyBuffer())
                    println(String(bodyStream.toByteArray()))
                })
    }
}