/*
 *  Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */
package org.wso2.carbon.analytics.dataservice.clustering;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.analytics.dataservice.AnalyticsServiceHolder;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.Member;
import com.hazelcast.core.MemberAttributeEvent;
import com.hazelcast.core.MembershipEvent;
import com.hazelcast.core.MembershipListener;

/**
 * This class represents an {@link AnalyticsClusterManager} implementation.
 */
public class AnalyticsClusterManagerImpl implements AnalyticsClusterManager, MembershipListener {

    private static final String ANALYTICS_GROUP_EXECUTOR_SERVICE_PREFIX = "_ANALYTICS_GROUP_EXECUTOR_SERVICE_";

    private static final String ANALYTICS_CLUSTER_GROUP_MEMBERS_PREFIX = "_ANALYTICS_CLUSTER_GROUP_MEMBERS_";
    
    private static final String ANALYTICS_CLUSTER_GROUP_DATA_PREFIX = "_ANALYTICS_CLUSTER_GROUP_DATA_";
    
    private static final Log log = LogFactory.getLog(AnalyticsClusterManagerImpl.class);
        
    private HazelcastInstance hz;
    
    private Map<String, GroupEventListener> groups = new HashMap<String, GroupEventListener>();
    
    public AnalyticsClusterManagerImpl() {
        this.hz = AnalyticsServiceHolder.getHazelcastInstance();
        if (this.isClusteringEnabled()) {
            this.hz.getCluster().addMembershipListener(this);
        }
    }

    @Override
    public void joinGroup(String groupId, GroupEventListener groupEventListener) throws AnalyticsClusterException {
        if (groupEventListener == null) {
            throw new IllegalArgumentException("The group event listener cannot be null");
        }
        if (!this.isClusteringEnabled()) {
            throw new AnalyticsClusterException("Clustering is not enabled");
        }
        if (this.groups.containsKey(groupId)) {
            throw new AnalyticsClusterException("This node has already joined the group: " + groupId);
        }
        this.checkAndCleanupGroups(groupId);
        this.groups.put(groupId, groupEventListener);
        List<Member> groupMembers = this.getGroupMembers(groupId);
        Member myself = this.hz.getCluster().getLocalMember();
        groupMembers.add(myself);
        if (groupMembers.get(0).equals(myself)) {
            this.executeMyselfBecomingLeader(groupId);
        }
    }
    
    private void executeMyselfBecomingLeader(String groupId) throws AnalyticsClusterException {
        this.groups.get(groupId).onBecomingLeader();
        this.execute(groupId, new LeaderUpdateNotification(groupId));
    }
    
    private String generateGroupListId(String groupId) {
        return ANALYTICS_CLUSTER_GROUP_MEMBERS_PREFIX + groupId;
    }
    
    private String generateGroupExecutorId(String groupId) {
        return ANALYTICS_GROUP_EXECUTOR_SERVICE_PREFIX + groupId;
    }
    
    private String generateGroupDataMapId(String groupId) {
        return ANALYTICS_CLUSTER_GROUP_DATA_PREFIX + groupId;
    }
    
    private List<Member> getGroupMembers(String groupId) {
        return this.hz.getList(this.generateGroupListId(groupId));
    }
    
    /**
     * This method checks the current active members to see if there any members left in the member list,
     * that is actually not in the cluster and clean it up. This can happen, when the last member of the group
     * also goes away, but the distributed memory is retained from other nodes in the cluster.
     * @param groupId The group id
     */
    private void checkAndCleanupGroups(String groupId) {
        List<Member> groupMembers = this.getGroupMembers(groupId);
        Set<Member> existingMembers = this.hz.getCluster().getMembers();
        Iterator<Member> memberItr = groupMembers.iterator();
        while (memberItr.hasNext()) {
            if (!existingMembers.contains(memberItr.next())) {
                memberItr.remove();
            }
        }
    }

    @Override
    public <T> List<T> execute(String groupId, Callable<T> callable) throws AnalyticsClusterException {
        if (!this.groups.containsKey(groupId)) {
            throw new AnalyticsClusterException("The node is required to join the group (" + 
                    groupId + ") before sending cluster messages");
        }
        List<Member> members = this.getGroupMembers(groupId);
        List<T> result = new ArrayList<T>();
        Map<Member, Future<T>> executionResult;
        executionResult = this.hz.getExecutorService(this.generateGroupExecutorId(groupId)).submitToMembers(
                callable, members);
        for (Map.Entry<Member, Future<T>> entry : executionResult.entrySet()) {
            try {
                result.add(entry.getValue().get());
            } catch (InterruptedException | ExecutionException e) {
                throw new AnalyticsClusterException("Error in cluster execute: " + e.getMessage(), e);
            }
        }
        return result;
    }
    
    @Override
    public void setProperty(String groupId, String name, Serializable value) {
        Map<String, Serializable> data = this.hz.getMap(this.generateGroupDataMapId(groupId));
        data.put(name, value);
    }

    @Override
    public Serializable getProperty(String groupId, String name) {
        Map<String, Serializable> data = this.hz.getMap(this.generateGroupDataMapId(groupId));
        return data.get(name);
    }

    @Override
    public boolean isClusteringEnabled() {
        return this.hz != null;
    }

    @Override
    public void memberAdded(MembershipEvent event) {
        /* nothing to do */
    }

    @Override
    public void memberAttributeChanged(MemberAttributeEvent event) {
        /* nothing to do */
    }

    private void checkGroupMemberRemoval(String groupId, Member member) throws AnalyticsClusterException {
        List<Member> groupMembers = this.getGroupMembers(groupId);
        if (groupMembers.contains(member)) {
            groupMembers.remove(member);
        }
        Member myself = this.hz.getCluster().getLocalMember();
        if (groupMembers.get(0).equals(myself)) {
            this.executeMyselfBecomingLeader(groupId);
        }
    }
    
    private void leaderUpdateNotificationReceived(String groupId) {
        GroupEventListener listener = this.groups.get(groupId);
        if (listener != null) {
            listener.onLeaderUpdate();
        }
    }
    
    @Override
    public void memberRemoved(MembershipEvent event) {
        Set<String> groupIds = this.groups.keySet();
        for (String groupId : groupIds) {
            try {
                this.checkGroupMemberRemoval(groupId, event.getMember());
            } catch (AnalyticsClusterException e) {
                log.error("Error in member removal: " + e.getMessage(), e);
            }
        }
    }
    
    /**
     * This class represents the cluster message that notifies the cluster of a new leader
     */
    public static class LeaderUpdateNotification implements Callable<String> {

        private String groupId;
        
        public LeaderUpdateNotification(String groupId) {
            this.groupId = groupId;
        }
        
        public String getGroupId() {
            return groupId;
        }
        
        @Override
        public String call() throws Exception {
            AnalyticsClusterManager cm = AnalyticsServiceHolder.getAnalyticsClusterManager();
            if (cm instanceof AnalyticsClusterManagerImpl) {
                AnalyticsClusterManagerImpl cmImpl = (AnalyticsClusterManagerImpl) cm;
                cmImpl.leaderUpdateNotificationReceived(this.getGroupId());
            }
            return "OK";
        }

    }
    
}
