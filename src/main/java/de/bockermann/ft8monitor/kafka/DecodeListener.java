package de.bockermann.ft8monitor.kafka;

import de.bockermann.ft8monitor.model.Decode;
import de.bockermann.ft8monitor.overview.GridFieldAggregator;
import de.bockermann.ft8monitor.qso.QsoGrouper;
import de.bockermann.ft8monitor.web.DecodeBroadcaster;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes decodes from the {@code ft8.decodes} Kafka topic, assigns each a
 * stable id ({@code partition@offset}), feeds it into the {@link QsoGrouper}
 * and pushes it to live SSE subscribers.
 */
@Component
public class DecodeListener {

    private static final Logger log = LoggerFactory.getLogger(DecodeListener.class);

    private final DecodeParser parser;
    private final QsoGrouper grouper;
    private final DecodeBroadcaster broadcaster;
    private final GridFieldAggregator gridAggregator;

    public DecodeListener(DecodeParser parser, QsoGrouper grouper, DecodeBroadcaster broadcaster,
                          GridFieldAggregator gridAggregator) {
        this.parser = parser;
        this.grouper = grouper;
        this.broadcaster = broadcaster;
        this.gridAggregator = gridAggregator;
    }

    @KafkaListener(topics = "${ft8.topic}", groupId = "${spring.kafka.consumer.group-id:ft8-monitor}")
    public void onDecode(ConsumerRecord<String, String> record) {
        String id = record.partition() + "@" + record.offset();
        Decode decode = parser.parse(record.value(), id);
        if (decode == null) {
            log.debug("Skipping unparseable decode at {}: {}", id, record.value());
            return;
        }
        // ft8mon's i3= fallback messages are usually false decodes of noise.
        if (decode.msg().startsWith("i3=")) {
            return;
        }
        grouper.add(decode);
        gridAggregator.add(decode.unix(), decode.msg());
        broadcaster.broadcast(decode);
    }
}
