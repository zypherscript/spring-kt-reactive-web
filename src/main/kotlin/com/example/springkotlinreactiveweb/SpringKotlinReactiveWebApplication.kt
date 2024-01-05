package com.example.springkotlinreactiveweb

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
import org.reactivestreams.Publisher
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import org.springframework.data.annotation.Id
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.http.server.reactive.ServerHttpResponse
import org.springframework.http.server.reactive.ServerHttpResponseDecorator
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import org.springframework.web.reactive.function.client.bodyToMono
import org.springframework.web.reactive.function.server.*
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.io.ByteArrayOutputStream


@SpringBootApplication
class SpringKotlinReactiveWebApplication {

    @Bean
    fun webClient() = WebClient.builder().build()

    @Bean
    fun init(
        customerRepository: CustomerRepository,
        reactiveCustomerRepository: ReactiveCustomerRepository
    ): CommandLineRunner {
        return CommandLineRunner {
            runBlocking {
                customerRepository.findAll()
                    .toList()
                    .forEach { println(it) }
                println("All customers printed<coroutine>!")
            }
            reactiveCustomerRepository.findAll()
                .subscribe(
                    { customer -> println(customer.toString()) },
                    { error -> System.err.println("Error occurred: $error") }
                )
                { println("All customers printed<reactive>!") }
        }
    }

    @Bean
    fun http(customerRepository: CustomerRepository) = coRouter {
        GET("/customers") {
            ServerResponse.ok().bodyAndAwait(customerRepository.findAll())
        }
        GET("/customers/{id}") { it ->
            val id = it.pathVariable("id").toInt();
            val result = customerRepository.findById(id)
//            if (result != null) {
//                ServerResponse.ok().bodyValueAndAwait(result)
//            } else {
//                ServerResponse.notFound().buildAndAwait()
//            }
            result?.let { ServerResponse.ok().bodyValueAndAwait(it) } ?: ServerResponse.notFound()
                .buildAndAwait()
        }
    }
}

@RestController
@RequestMapping("/reactivecustomers")
class ProductController {

    @Autowired
    lateinit var customerRepository: ReactiveCustomerRepository

    @GetMapping("/{id}")
    fun customer(@PathVariable id: Int): Mono<Customer> {
        return customerRepository.findById(id)
    }

    @GetMapping
    fun customers(): Flux<Customer> {
        return customerRepository.findAll()
    }
}

@RestController
@RequestMapping("/cwc")
class ProductControllerCoroutines {
    @Autowired
    lateinit var webClient: WebClient

    @Autowired
    lateinit var customerRepository: CustomerRepository

    @Autowired
    lateinit var reactiveCustomerRepository: ReactiveCustomerRepository

    @GetMapping("/{id}")
    suspend fun customerWithCatFact(@PathVariable id: Int): String {
        val customer = customerRepository.findById(id)
        val catFact = webClient.get()
            .uri("https://catfact.ninja/fact")
            .accept(APPLICATION_JSON)
            .retrieve()
            .awaitBody<CatFact>()
        return "${customer?.name} : ${catFact.fact}"
    }

    @GetMapping("/p/{id}")
    suspend fun pCustomerWithCatFact(@PathVariable id: Int): String = coroutineScope {
        val customer: Deferred<Customer?> = async(start = CoroutineStart.LAZY) {
            customerRepository.findById(id)
        }
        val catFact: Deferred<CatFact> = async(start = CoroutineStart.LAZY) {
            webClient.get()
                .uri("https://catfact.ninja/fact")
                .accept(APPLICATION_JSON)
                .retrieve().awaitBody<CatFact>()
        }
        customer.start()
        catFact.start()
        "${customer.await()?.name} : ${catFact.await().fact}"
    }

    @GetMapping("/r/{id}")
    fun npCustomerWithCatFact(@PathVariable id: Int): Mono<String> {
        val customer = reactiveCustomerRepository.findById(id)

        val catFact = webClient.get()
            .uri("https://catfact.ninja/fact")
            .accept(APPLICATION_JSON)
            .retrieve()
            .bodyToMono<CatFact>()
        return customer.zipWith(catFact) { cs, cf ->
            "${cs.name} : ${cf.fact}"
        }
    }
}

data class CatFact(val fact: String)

fun main(args: Array<String>) {
    runApplication<SpringKotlinReactiveWebApplication>(*args)
}

interface CustomerRepository : CoroutineCrudRepository<Customer, Int>

interface ReactiveCustomerRepository : ReactiveCrudRepository<Customer, Int>

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
                .map { buffer ->
                    val bytes = ByteArray(buffer.readableByteCount())
                    buffer.read(bytes)
                    DataBufferUtils.release(buffer)
                    bytes
                }
                .map { bytes ->
                    val bodyStream = ByteArrayOutputStream()
                    bodyStream.write(bytes)
                    println(String(bodyStream.toByteArray()))
                    bytes
                }
                .map { bytes -> DefaultDataBufferFactory().wrap(bytes) }
        )
    }
}