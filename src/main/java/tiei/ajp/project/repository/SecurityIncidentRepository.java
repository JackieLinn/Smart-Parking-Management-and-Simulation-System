/*
 * Group members
 * 2026248015 Lin Xiaoyi
 * 2026248027 Ren Zongxuan
 * 2026248068 Tang Chuanyue
 */

package tiei.ajp.project.repository;

import tiei.ajp.project.model.SecurityIncident;

import java.util.List;

/**
 * Stores wrong-parking incidents with their patrol assignments, reports, and fines.
 */
public interface SecurityIncidentRepository {
    long nextId();

    void save(SecurityIncident incident);

    List<SecurityIncident> findAll();
}
