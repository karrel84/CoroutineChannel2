package me.rell.coroutinechannel2

import org.junit.Assert.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {

    @Test
    fun `Channel basics`() {
        val channel = Channel<Int>()
        GlobalScope.launch {
            // this might be heavy CPU-consuming computation or async logic, we'll just send five squares
            for (x in 1..5) channel.send(x * x)


        }
        // here we print five received integers:
        GlobalScope.launch {
            repeat(5) { println("coroutinechannel > ${channel.receive()}") }
        }

        println("coroutinechannel Done!")

        Thread.sleep(100L)
    }

    @Test
    fun `채널을 통한 닫기 및 반복`() {
        val channel = Channel<Int>()
        GlobalScope.launch {
            for (x in 1..5) {
                if (channel.isClosedForSend) {
                    return@launch
                }
                channel.send(x * x)
            }
        }
        GlobalScope.launch {
            delay(100)
            channel.close() // we're done sending
        }
        // here we print received values using `for` loop (until the channel is closed)
        GlobalScope.launch {
            for (y in channel) {
                delay(50)
                println(y)
            }
        }

        println("Done!")

        Thread.sleep(1000L)
    }

    @Test
    fun `channel producer`() {
        val squares = GlobalScope.produceSquares()
        GlobalScope.launch {
            squares.consumeEach { println(it) }
        }

        println("Done!")

        Thread.sleep(1000L)
    }

    private fun CoroutineScope.produceSquares(): ReceiveChannel<Int> = produce {
        for (x in 1..5) send(x * x)
    }


    @Test
    fun `pipe line`() {
        val numbers = GlobalScope.produceNumbers() // produces integers from 1 and on
        val squares = GlobalScope.square(numbers) // squares integers

        GlobalScope.launch {
            repeat(15) {
                println(squares.receive()) // print first five
            }
            println("Done!") // we are done
            coroutineContext.cancelChildren() // cancel children coroutines
        }

        Thread.sleep(1000L)
    }


    fun CoroutineScope.produceNumbers() = produce<Int> {
        var x = 1
        while (true) send(x++) // infinite stream of integers starting from 1
    }

    fun CoroutineScope.square(numbers: ReceiveChannel<Int>): ReceiveChannel<Int> = produce {
        for (x in numbers) send(x * x)
    }


    fun CoroutineScope.numbersFrom(start: Int) = produce {
        var x = start
        while (true) send(x++) // infinite stream of integers from start
    }

    fun CoroutineScope.filter(numbers: ReceiveChannel<Int>, prime: Int) = produce {
        for (x in numbers) if (x % prime != 0) send(x)
    }

    @Test
    fun `prime number with pipe line`() {
        GlobalScope.launch {
            var cur = numbersFrom(2)
            repeat(10) {
                val prime = cur.receive()
                println(prime)
                cur = filter(cur, prime)
            }
            coroutineContext.cancelChildren() // cancel all children to let main finish
        }
        Thread.sleep(1000L)

    }


    fun CoroutineScope.produceNumbers2() = produce {
        var x = 1 // start from 1
        while (true) {
            send(x++) // produce next
            delay(100) // wait 0.1s
        }
    }

    fun CoroutineScope.launchProcessor(id: Int, channel: ReceiveChannel<Int>) = launch {
        for (msg in channel) {
            println("Processor #$id received $msg")
        }
    }

    @Test
    fun `Fan-out`() {
        GlobalScope.launch {
            val producer = produceNumbers2()
            repeat(5) { launchProcessor(it, producer) }
            delay(950)
            producer.cancel() // cancel producer coroutine and thus kill them all
        }
        Thread.sleep(1000L)

    }

    suspend fun sendString(channel: SendChannel<String>, s: String, time: Long) {
        while (true) {
            delay(time)
            channel.send(s)
        }
    }

    @Test
    fun `Fan-in`() {
        GlobalScope.launch {
            val channel = Channel<String>()
            launch { sendString(channel, "foo", 200L) }
            launch { sendString(channel, "BAR!", 500L) }
            repeat(6) { // receive first six
                println(channel.receive())
            }
            coroutineContext.cancelChildren() // cancel all children to let main finish
        }

        Thread.sleep(1000L)
    }

    @Test
    fun `Buffered channel`() {
        GlobalScope.launch {
            val channel = Channel<Int>(4) // create buffered channel
            val sender = launch { // launch sender coroutine
                repeat(10) {
                    println("Sending $it") // print before sending each element
                    channel.send(it) // will suspend when buffer is full
                }
            }
            // don't receive anything... just wait....
            delay(1000)
            sender.cancel() // cancel sender coroutine
        }
        Thread.sleep(1000L)

    }

    data class Ball(var hits: Int)


    suspend fun player(name: String, table: Channel<Ball>) {
        for (ball in table) { // receive the ball in a loop
            ball.hits++
            println("$name $ball")
            delay(300) // wait a bit
            table.send(ball) // send the ball back
        }
    }

    @Test
    fun `channels are fair`(){
        runBlocking {
            val table = Channel<Ball>() // a shared table
            launch { player("ping", table) }
            launch { player("pong", table) }
            table.send(Ball(0)) // serve the ball
            delay(1000) // delay 1 second
            coroutineContext.cancelChildren() // game over, cancel them
        }
    }

    @Test
    fun `ticker channel`() = runBlocking {
        val tickerChannel = ticker(delayMillis = 100, initialDelayMillis = 0) // create ticker channel
        var nextElement = withTimeoutOrNull(1) { tickerChannel.receive() }
        println("Initial element is available immediately: $nextElement") // no initial delay

        nextElement = withTimeoutOrNull(50) { tickerChannel.receive() } // all subsequent elements have 100ms delay
        println("Next element is not ready in 50 ms: $nextElement")

        nextElement = withTimeoutOrNull(60) { tickerChannel.receive() }
        println("Next element is ready in 100 ms: $nextElement")

        // Emulate large consumption delays
        println("Consumer pauses for 150ms")
        delay(150)
        // Next element is available immediately
        nextElement = withTimeoutOrNull(1) { tickerChannel.receive() }
        println("Next element is available immediately after large consumer delay: $nextElement")
        // Note that the pause between `receive` calls is taken into account and next element arrives faster
        nextElement = withTimeoutOrNull(60) { tickerChannel.receive() }
        println("Next element is ready in 50ms after consumer pause in 150ms: $nextElement")

        tickerChannel.cancel() // indicate that no more elements are needed
    }

}