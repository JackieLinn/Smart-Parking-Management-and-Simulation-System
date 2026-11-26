package tiei.ajp.project.repository;

import tiei.ajp.project.model.SecurityIncident;

import java.util.List;

/**
 * 保存错停告警及其关联的派单、反馈和罚款。
 */
public interface SecurityIncidentRepository {
    long nextId();

    void save(SecurityIncident incident);

    List<SecurityIncident> findAll();
}
