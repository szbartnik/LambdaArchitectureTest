package com.sbartnik.common

import java.util.Properties

import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import org.slf4j.LoggerFactory

import scala.util.Random

class KafkaProducerOperations(configProps: Properties, topic: String, numOfPartitions: Int) {

  private val logger = LoggerFactory.getLogger(this.getClass)

  private val kafkaProducer = new KafkaProducer[String, String](configProps)
  logger.info(s"Initialized KafkaProducerOperations with topic '$topic' and properties: ${configProps.toString}")
  logger.info(s"Topic $topic has ${kafkaProducer.partitionsFor(topic)} partitions")

  private val rnd = new Random()

  def send(message: String): Boolean = {
    try {
      logger.info(s"Sending message to topic $topic...")
      val partition = rnd.nextInt(numOfPartitions).toString
      val recordToSend = new ProducerRecord[String, String](topic, partition, message)
      kafkaProducer.send(recordToSend)
      true
    } catch {
      case ex: Exception =>
        logger.error(s"Got exception sending message to topic $topic", ex)
        false
    }
  }
}
