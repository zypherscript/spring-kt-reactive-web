package com.example.springkotlinreactiveweb

import kotlinx.coroutines.flow.count
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

@SpringBootTest
@Import(TestSpringKotlinReactiveWebApplication::class)
class SpringKotlinReactiveWebApplicationTests(
        @Autowired val customerRepository: CustomerRepository
) {

    @Test
    fun contextLoads() {
        runBlocking {
            customerRepository.save(Customer(null, "test3"))
            val customers = customerRepository.findAll()
            Assertions.assertEquals(customers.count(), 3)
        }
    }

}
