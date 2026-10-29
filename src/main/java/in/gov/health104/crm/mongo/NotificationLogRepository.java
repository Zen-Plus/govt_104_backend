package in.gov.health104.crm.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface NotificationLogRepository extends MongoRepository<NotificationLog, String> {
    List<NotificationLog> findByGrievanceNoOrderByTimestampDesc(String grievanceNo);
}
