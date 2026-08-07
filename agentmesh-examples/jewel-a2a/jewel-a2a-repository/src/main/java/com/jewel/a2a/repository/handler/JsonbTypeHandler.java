package com.jewel.a2a.repository.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;
import org.postgresql.util.PGobject;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * PostgreSQL JSONB 类型处理器
 */
@MappedTypes({Object.class})
public class JsonbTypeHandler extends BaseTypeHandler<Object> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String JSONB_TYPE = "jsonb";

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Object parameter, JdbcType jdbcType) throws SQLException {
        PGobject pgObject = new PGobject();
        pgObject.setType(JSONB_TYPE);
        try {
            pgObject.setValue(MAPPER.writeValueAsString(parameter));
        } catch (JsonProcessingException e) {
            throw new SQLException("JSONB 序列化失败", e);
        }
        ps.setObject(i, pgObject);
    }

    @Override
    public Object getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parseJson(rs.getObject(columnName));
    }

    @Override
    public Object getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parseJson(rs.getObject(columnIndex));
    }

    @Override
    public Object getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parseJson(cs.getObject(columnIndex));
    }

    private Object parseJson(Object value) {
        if (value instanceof PGobject pgObject) {
            try {
                return MAPPER.readValue(pgObject.getValue(), Object.class);
            } catch (JsonProcessingException e) {
                return pgObject.getValue();
            }
        }
        return value;
    }
}
