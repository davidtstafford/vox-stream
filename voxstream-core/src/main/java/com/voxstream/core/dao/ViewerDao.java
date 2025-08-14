package com.voxstream.core.dao;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.voxstream.core.model.Viewer;

public interface ViewerDao {
    void upsert(Viewer viewer);

    Optional<Viewer> findById(String id);

    Optional<Viewer> findByPlatformHandle(String platform, String handle);

    List<Viewer> recent(int limit);

    void touch(String id, Instant when);
}
