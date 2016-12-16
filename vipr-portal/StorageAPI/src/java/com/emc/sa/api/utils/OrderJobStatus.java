/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.api.utils;

import com.emc.storageos.coordinator.client.model.CoordinatorClassInfo;
import com.emc.storageos.coordinator.client.model.CoordinatorSerializable;
import com.emc.storageos.coordinator.exceptions.CoordinatorException;
import com.emc.storageos.coordinator.exceptions.FatalCoordinatorException;
import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.List;

public class OrderJobStatus implements CoordinatorSerializable {
    private static final Logger log = LoggerFactory.getLogger(OrderJobStatus.class);

    private static final ObjectMapper mapper = new ObjectMapper().enableDefaultTyping();

    private long startTime;
    private long endTime;
    private List<URI> tids;

    private long total = -1;
    private long nCompleted =0; // Number of Orders has been deleted or downloaded so far
    private long nFailed = 0;  // Number of Orders failed to be deleted or downloaded so far
    private long timeUsedPerOrder = -1;  //The time used to delete or download an order

    // used to deserialize from ZK
    public OrderJobStatus() {

    }

    public OrderJobStatus(long startTime, long endTime, List<URI> tids) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.tids = tids;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public long getnCompleted() {
        return nCompleted;
    }

    public void increaseCompleted(long inc) {
        nCompleted += inc;
    }

    public long getFailed() {
        return nFailed;
    }

    public void setFailed(long n) {
        nFailed = n;
    }

    public long getTimeUsedPerOrder() {
        return timeUsedPerOrder;
    }

    public void setTimeUsedPerOrder(long timeUsedPerOrder) {
        this.timeUsedPerOrder = timeUsedPerOrder;
    }

    @Override
    public String encodeAsString() {
        return toString();
    }

    @Override
    public OrderJobStatus decodeFromString(String infoStr) throws FatalCoordinatorException {
        try {
            mapper.readerForUpdating(this).readValue(infoStr);
            return this;
        } catch (IOException e) {
            log.error("Failed to decode data string", e);
            throw CoordinatorException.fatals.decodingError(e.getMessage());
        }
    }

    @Override
    public String toString() {
        try {
            return mapper.writeValueAsString(this);
        } catch (IOException e) {
            log.error("Failed to serialize this object", e);
            return null;
        }
    }

    @Override
    @JsonIgnore
    public CoordinatorClassInfo getCoordinatorClassInfo() {
        return null;
    }
}
