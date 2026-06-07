package com.hmdm.persistence.mapper;

import com.hmdm.persistence.domain.DeviceAppProposal;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface DeviceAppProposalMapper {

    @Select("SELECT * FROM device_app_proposal WHERE deviceId = #{deviceId} LIMIT 1")
    DeviceAppProposal findByDeviceId(@Param("deviceId") Integer deviceId);

    @Select("SELECT * FROM device_app_proposal WHERE id = #{id} LIMIT 1")
    DeviceAppProposal findById(@Param("id") Integer id);

    @Insert("INSERT INTO device_app_proposal (deviceId, customerId, proposalJson, status, createdAt) " +
            "VALUES (#{deviceId}, #{customerId}, #{proposalJson}, 'PENDING', " +
            "CAST(EXTRACT(EPOCH FROM NOW()) * 1000 AS BIGINT))")
    @SelectKey(statement = "SELECT currval('device_app_proposal_id_seq')",
               keyColumn = "id", keyProperty = "id", before = false, resultType = int.class)
    void insert(DeviceAppProposal proposal);

    @Update("UPDATE device_app_proposal " +
            "SET proposalJson = #{proposalJson}, status = 'PENDING', " +
            "    createdAt = CAST(EXTRACT(EPOCH FROM NOW()) * 1000 AS BIGINT) " +
            "WHERE deviceId = #{deviceId}")
    void updateProposal(@Param("deviceId") Integer deviceId, @Param("proposalJson") String proposalJson);

    @Update("UPDATE device_app_proposal " +
            "SET status = #{status}, appliedConfigurationId = #{appliedConfigurationId} " +
            "WHERE id = #{id}")
    void updateStatus(@Param("id") Integer id,
                      @Param("status") String status,
                      @Param("appliedConfigurationId") Integer appliedConfigurationId);

    @Select("SELECT deviceId, proposalJson " +
            "FROM device_app_proposal " +
            "WHERE customerId = #{customerId} AND status = 'PENDING'")
    List<DeviceAppProposal> findPendingByCustomerId(@Param("customerId") Integer customerId);
}
