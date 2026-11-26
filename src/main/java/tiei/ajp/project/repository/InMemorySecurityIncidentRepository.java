/*
 * Group members
 * 2026248015 Lin Xiaoyi
 * 2026248027 Ren Zongxuan
 * 2026248068 Tang Chuanyue
 */

package tiei.ajp.project.repository;

import tiei.ajp.project.model.SecurityIncident;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class InMemorySecurityIncidentRepository implements SecurityIncidentRepository {
    private final Map<Long, SecurityIncident> incidents = new LinkedHashMap<>();
    private long nextId = 1L;

    @Override
    public long nextId() {
        return nextId++;
    }

    @Override
    public void save(SecurityIncident incident) {
        incidents.put(incident.id(), incident);
    }

    @Override
    public List<SecurityIncident> findAll() {
        return List.copyOf(incidents.values());
    }
}
