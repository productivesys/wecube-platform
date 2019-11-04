package com.webank.wecube.platform.core.service.resource;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Service;

import com.google.common.base.Strings;
import com.webank.wecube.platform.core.commons.ApplicationProperties.ResourceProperties;
import com.webank.wecube.platform.core.commons.WecubeCoreException;
import com.webank.wecube.platform.core.domain.ResourceItem;
import com.webank.wecube.platform.core.utils.EncryptionUtils;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class MysqlAccountManagementService implements ResourceItemService {

    @Autowired
    private ResourceProperties resourceProperties;

    public DriverManagerDataSource newMysqlDatasource(String host, String port, String username, String password) {
        return newMysqlDatasource(host,port,username,password,null);
    }

    public DriverManagerDataSource newMysqlDatasource(String host, String port, String username, String password, String database) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        if(Strings.isNullOrEmpty(database)) {
            dataSource.setUrl("jdbc:mysql://" + host + ":" + port + "?characterEncoding=utf8&serverTimezone=UTC");
        }else {
            dataSource.setUrl("jdbc:mysql://" + host + ":" + port +"/"+ database +"?characterEncoding=utf8&serverTimezone=UTC");
        }
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        return dataSource;
    }

    @Override
    public ResourceItem createItem(ResourceItem item) {
        Map<String, String> additionalProperties = item.getAdditionalPropertiesMap();
        String username = additionalProperties.get("username");
        String password = additionalProperties.get("password");
        if (username == null || password == null) {
            throw new WecubeCoreException("Username or password is missing");
        }

        DriverManagerDataSource dataSource = newDatasource(item);
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();) {
            log.info("password before decrypt={}", password);
            String rawPassword = EncryptionUtils.decryptWithAes(password,
                    resourceProperties.getPasswordEncryptionSeed(), item.getName());
            statement.executeUpdate(String.format("CREATE USER `%s` IDENTIFIED BY '%s'", username, rawPassword));
            statement.executeUpdate(String.format("GRANT ALL ON %s.* TO %s@'%%' IDENTIFIED BY '%s'", item.getName(),
                    username, rawPassword));
        } catch (Exception e) {
            String errorMessage = String.format("Failed to create account [username = %s]", username);
            log.error(errorMessage);
            throw new WecubeCoreException(errorMessage, e);
        }
        return item;
    }

    @Override
    public void deleteItem(ResourceItem item) {
        DriverManagerDataSource dataSource = newDatasource(item);
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();) {
            statement.executeUpdate(String.format("DROP USER %s", item.getName()));
        } catch (SQLException e) {
            String errorMessage = String.format("Failed to drop account [username = %s]", item.getName());
            log.error(errorMessage);
            throw new WecubeCoreException(errorMessage, e);
        }
    }

    private DriverManagerDataSource newDatasource(ResourceItem item) {
        String password;
        try {
            password = EncryptionUtils.decryptWithAes(item.getResourceServer().getLoginPassword(),
                    resourceProperties.getPasswordEncryptionSeed(), item.getResourceServer().getName());
        } catch (Exception e) {
            throw new WecubeCoreException(
                    String.format("Failed to decrypt the login password of server [%s].", item.getResourceServer()), e);
        }

        DriverManagerDataSource dataSource = newMysqlDatasource(item.getResourceServer().getHost(),
                item.getResourceServer().getPort(), item.getResourceServer().getLoginUsername(), password);
        return dataSource;
    }

    @Override
    public ResourceItem retrieveItem(ResourceItem item) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ResourceItem updateItem(ResourceItem item) {
        throw new UnsupportedOperationException();
    }
}
