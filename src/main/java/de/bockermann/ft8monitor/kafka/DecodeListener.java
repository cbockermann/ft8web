package de.bockermann.ft8monitor.kafka;

import de.bockermann.ft8monitor.model.Decode;
import de.bockermann.ft8monitor.overview.GridFieldAggregator;
import de.bockermann.ft8monitor.qso.QsoGrouper;
import de.bockermann.ft8monitor.web.DecodeBroadcaster;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.ConsumerSeekAware;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Consumes decodes from the {@code ft8.decodes} Kafka topic, assigns each a
 * stable id ({@code partition@offset}), feeds it into the {@link QsoGrouper} and
 * the {@link GridFieldAggregator}, and pushes it to live SSE subscribers.
 *
 * <p>On startup it can replay the last {@code ft8.backfill-minutes} so the
 * in-memory views (recent decodes, QSOs, activity map) are repopulated after a
 * restart. The rewind runs once per partition per process (not on later
 * rebalances) so it never double-counts, and it only applies to this display
 * consumer — not to other consumer groups such as the PSKReporter feeder.
 */
@Component
public class DecodeListener implements ConsumerSeekAware {

    private static final Logger log = LoggerFactory.getLogger(DecodeListener.class);

    private final DecodeParser parser;
    private final QsoGrouper grouper;
    private final DecodeBroadcaster broadcaster;
    private final GridFieldAggregator gridAggregator;
    private final int backfillMinutes;

    /** Partitions already rewound this process; prevents replay on rebalances. */
    private final Set<TopicPartition> backfilled = ConcurrentHashMap.newKeySet();

    public DecodeListener(DecodeParser parser, QsoGrouper grouper, DecodeBroadcaster broadcaster,
                          GridFieldAggregator gridAggregator,
                          @Value("${ft8.backfill-minutes:15}") int backfillMinutes) {
        this.parser = parser;
        this.grouper = grouper;
        this.broadcaster = broadcaster;
        this.gridAggregator = gridAggregator;
        this.backfillMinutes = backfillMinutes;
    }

    @Override
    public void onPartitionsAssigned(Map<TopicPartition, Long> assignments, ConsumerSeekCallback callback) {
        if (backfillMinutes <= 0) {
            return;
        }
        long timestamp = System.currentTimeMillis() - backfillMinutes * 60_000L;
        for (TopicPartition tp : assignments.keySet()) {
            if (backfilled.add(tp)) { // only the first time we see this partition
                callback.seekToTimestamp(tp.topic(), tp.partition(), timestamp);
                log.info("Backfill: rewinding {} to ~{} min ago", tp, backfillMinutes);
            }
        }
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
