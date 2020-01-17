package com.webank.wecube.platform.core.service.user;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import com.webank.wecube.platform.core.commons.ApplicationProperties;
import com.webank.wecube.platform.core.commons.WecubeCoreException;
import com.webank.wecube.platform.core.dto.CommonResponseDto;
import com.webank.wecube.platform.core.dto.user.RoleDto;
import com.webank.wecube.platform.core.dto.user.UserDto;
import com.webank.wecube.platform.core.support.RestClientException;
import com.webank.wecube.platform.core.support.authserver.AsUserDto;
import com.webank.wecube.platform.core.support.authserver.AuthServerRestClient;
import com.webank.wecube.platform.core.utils.JsonUtils;
import com.webank.wecube.platform.core.utils.RestTemplateUtils;

@Service
public class UserManagementServiceImpl implements UserManagementService {
    private final static Logger log = LoggerFactory.getLogger(UserManagementServiceImpl.class);
    // request URLs
    private static final String GATEWAY_PLACE_HOLDER = "gatewayUrl";
    private static final String USER_NAME_PLACE_HOLDER = "userName";
    private static final String ROLE_ID_PLACE_HOLDER = "roleId";
    private static final String AUTH_SERVER_ROLE_CREATE_URL = "http://{" + GATEWAY_PLACE_HOLDER + "}/auth/v1/roles";
    private static final String AUTH_SERVER_ROLE_RETRIEVE_URL = "http://{" + GATEWAY_PLACE_HOLDER + "}/auth/v1/roles";
    private static final String AUTH_SERVER_ROLE_RETRIEVE_ROLE_ID_URL = "http://{" + GATEWAY_PLACE_HOLDER
            + "}/auth/v1/roles/{" + ROLE_ID_PLACE_HOLDER + "}";
    private static final String AUTH_SERVER_ROLE_DELETE_URL = "http://{" + GATEWAY_PLACE_HOLDER + "}/auth/v1/roles/{"
            + ROLE_ID_PLACE_HOLDER + "}";
    private static final String AUTH_SERVER_USER2ROLE_URL = "http://{" + GATEWAY_PLACE_HOLDER + "}/auth/v1/users/{"
            + USER_NAME_PLACE_HOLDER + "}/roles";
    private static final String AUTH_SERVER_ROLE2USER_URL = "http://{" + GATEWAY_PLACE_HOLDER + "}/auth/v1/roles/{"
            + ROLE_ID_PLACE_HOLDER + "}/users";
    private static final String AUTH_SERVER_GRANT_URL = "http://{" + GATEWAY_PLACE_HOLDER + "}/auth/v1/roles/{"
            + ROLE_ID_PLACE_HOLDER + "}/users";
    private static final String AUTH_SERVER_REVOKE_URL = "http://{" + GATEWAY_PLACE_HOLDER + "}/auth/v1/roles/{"
            + ROLE_ID_PLACE_HOLDER + "}/users";

    @Autowired
    @Qualifier(value = "userJwtSsoTokenRestTemplate")
    private RestTemplate restTemplate;

    @Autowired
    private AuthServerRestClient authServerRestClient;

    @Autowired
    private ApplicationProperties applicationProperties;

    @Override
    public UserDto registerUser(UserDto userDto) {
        if (userDto == null) {
            throw new IllegalArgumentException();
        }

        if (StringUtils.isBlank(userDto.getUsername()) || StringUtils.isBlank(userDto.getPassword())) {
            throw new WecubeCoreException("Username and password cannot be blank.");
        }

        AsUserDto reqUserDto = new AsUserDto();
        reqUserDto.setUsername(userDto.getUsername());
        reqUserDto.setPassword(userDto.getPassword());

        try {
            AsUserDto respUserDto = authServerRestClient.registerLocalUser(reqUserDto);

            UserDto result = new UserDto();
            result.setUsername(respUserDto.getUsername());
            result.setPassword(respUserDto.getPassword());
            result.setId(respUserDto.getId());
            return result;
        } catch (RestClientException e) {
            log.error("registering user failed", e);
            throw new WecubeCoreException("Failed to register user,caused by: " + e.getErrorMessage());
        }
    }

    @Override
    public List<UserDto> retrieveAllUserAccounts() {
        try {
            List<AsUserDto> asUserDtos = authServerRestClient.retrieveAllUserAccounts();
            List<UserDto> userDtos = new ArrayList<>();
            asUserDtos.forEach(m -> {
                UserDto u = new UserDto();
                u.setId(m.getId());
                u.setUsername(m.getUsername());
                u.setPassword(m.getPassword());
                
                userDtos.add(u);
            });
            
            return userDtos;
        } catch (RestClientException e) {
            log.error("failed to retrieve all user accounts", e);
            throw new WecubeCoreException("Failed to retrieve all user accounts.");
        }
    }

    @Override
    public void deleteUserByUserId(String userId) {
        //
        try{
            authServerRestClient.deleteUserAccountByUserId(userId);
        }catch (RestClientException e) {
            log.error("failed to delete user account by user id", e);
            throw new WecubeCoreException("Failed to delete user account.");
        }
    }

    @Override
    public CommonResponseDto createRole(String token, Map<String, Object> jsonObject) {
        HttpHeaders httpHeaders = createHeaderWithToken(token);
        Map<String, String> requestUrlMap = new HashMap<>();
        requestUrlMap.put(GATEWAY_PLACE_HOLDER, applicationProperties.getGatewayUrl());
        String requestUrl = generateRequestUrl(AUTH_SERVER_ROLE_CREATE_URL, requestUrlMap);
        log.info(String.format("Sending POST request to: [%s] with body: [%s]", requestUrl, jsonObject));
        ResponseEntity<String> response = RestTemplateUtils.sendPostRequestWithBody(this.restTemplate, requestUrl,
                httpHeaders, jsonObject);
        return RestTemplateUtils.checkResponse(response);
    }

    @Override
    public RoleDto createRole(RoleDto roleDto) throws WecubeCoreException {
        String token = "Bearer";
        Map<String, Object> createRoleMap = dtoToMap(roleDto);
        CommonResponseDto createRoleResponse = createRole(token, createRoleMap);
        String createRoleResponseDataJsonString = JsonUtils.toJsonString(createRoleResponse.getData());
        RoleDto resultDto;
        try {
            resultDto = JsonUtils.toObject(createRoleResponseDataJsonString, RoleDto.class);
        } catch (IOException ex) {
            String msg = String.format("Cannot transfer response's data [%s] to RoleId dto",
                    createRoleResponseDataJsonString);
            log.error(msg);
            throw new WecubeCoreException(msg);
        }
        return resultDto;
    }

    @Override
    public CommonResponseDto retrieveRole(String token) {
        HttpHeaders httpHeaders = createHeaderWithToken(token);
        Map<String, String> requestUrlMap = new HashMap<>();
        requestUrlMap.put(GATEWAY_PLACE_HOLDER, applicationProperties.getGatewayUrl());
        String requestUrl = generateRequestUrl(AUTH_SERVER_ROLE_RETRIEVE_URL, requestUrlMap);
        log.info(String.format("Sending GET request to: [%s]", requestUrl));
        ResponseEntity<String> response = RestTemplateUtils.sendGetRequestWithUrlParamMap(this.restTemplate, requestUrl,
                httpHeaders);
        return RestTemplateUtils.checkResponse(response);
    }

    @Override
    public List<RoleDto> retrieveRole() throws WecubeCoreException {
        String token = "Bearer";
        CommonResponseDto commonResponseDto = retrieveRole(token);
        String responseDataString = JsonUtils.toJsonString(commonResponseDto.getData());
        List<RoleDto> resultDto;
        try {
            resultDto = JsonUtils.toList(responseDataString, RoleDto.class);
        } catch (IOException ex) {
            String msg = String.format("Cannot transfer response's data [%s] to RoleId dto",
                    commonResponseDto.getData().toString());
            log.error(msg);
            throw new WecubeCoreException(msg);
        }
        return resultDto;
    }

    @Override
    public CommonResponseDto retrieveRoleById(String token, String roleId) {
        HttpHeaders httpHeaders = createHeaderWithToken(token);
        Map<String, String> requestUrlMap = new HashMap<>();
        requestUrlMap.put(GATEWAY_PLACE_HOLDER, applicationProperties.getGatewayUrl());
        requestUrlMap.put(ROLE_ID_PLACE_HOLDER, roleId);
        String requestUrl = generateRequestUrl(AUTH_SERVER_ROLE_RETRIEVE_ROLE_ID_URL, requestUrlMap);
        log.info(String.format("Sending GET request to: [%s]", requestUrl));
        ResponseEntity<String> response = RestTemplateUtils.sendGetRequestWithUrlParamMap(this.restTemplate, requestUrl,
                httpHeaders);
        return RestTemplateUtils.checkResponse(response);
    }

    @Override
    public CommonResponseDto deleteRole(String token, String roleId) {
        HttpHeaders httpHeaders = createHeaderWithToken(token);
        Map<String, String> requestUrlMap = new HashMap<>();
        requestUrlMap.put(GATEWAY_PLACE_HOLDER, applicationProperties.getGatewayUrl());
        requestUrlMap.put(ROLE_ID_PLACE_HOLDER, roleId);
        String requestUrl = generateRequestUrl(AUTH_SERVER_ROLE_DELETE_URL, requestUrlMap);
        log.info(String.format("Sending DELETE request to: [%s]", requestUrl));
        ResponseEntity<String> response = RestTemplateUtils.sendDeleteWithoutBody(this.restTemplate, requestUrl,
                httpHeaders);
        return RestTemplateUtils.checkResponse(response);
    }

    @Override
    public CommonResponseDto getRolesByUserName(String token, String username) {
        HttpHeaders httpHeaders = createHeaderWithToken(token);
        Map<String, String> requestUrlMap = new HashMap<>();
        requestUrlMap.put(GATEWAY_PLACE_HOLDER, applicationProperties.getGatewayUrl());
        requestUrlMap.put(USER_NAME_PLACE_HOLDER, username);
        String requestUrl = generateRequestUrl(AUTH_SERVER_USER2ROLE_URL, requestUrlMap);
        log.info(String.format("Sending GET request to: [%s]", requestUrl));
        ResponseEntity<String> response = RestTemplateUtils.sendGetRequestWithUrlParamMap(this.restTemplate, requestUrl,
                httpHeaders);
        return RestTemplateUtils.checkResponse(response);
    }

    @Override
    public List<String> getRoleIdListByUsername(String token, String username) {
        List<RoleDto> roleListByUserName = this.getRoleListByUserName(token, username);
        return roleListByUserName.stream().map(RoleDto::getId).collect(Collectors.toList());
    }

    @Override
    public CommonResponseDto getUsersByRoleId(String token, String roleId) {
        HttpHeaders httpHeaders = createHeaderWithToken(token);
        Map<String, String> requestUrlMap = new HashMap<>();
        requestUrlMap.put(GATEWAY_PLACE_HOLDER, applicationProperties.getGatewayUrl());
        requestUrlMap.put(ROLE_ID_PLACE_HOLDER, String.valueOf(roleId));
        String requestUrl = generateRequestUrl(AUTH_SERVER_ROLE2USER_URL, requestUrlMap);
        log.info(String.format("Sending GET request to: [%s]", requestUrl));
        ResponseEntity<String> response = RestTemplateUtils.sendGetRequestWithUrlParamMap(this.restTemplate, requestUrl,
                httpHeaders);
        return RestTemplateUtils.checkResponse(response);
    }

    @Override
    public CommonResponseDto grantRoleToUsers(String token, String roleId, List<Object> userIdList) {
        HttpHeaders httpHeaders = createHeaderWithToken(token);
        Map<String, String> requestUrlMap = new HashMap<>();
        requestUrlMap.put(GATEWAY_PLACE_HOLDER, applicationProperties.getGatewayUrl());
        requestUrlMap.put(ROLE_ID_PLACE_HOLDER, String.valueOf(roleId));
        String requestUrl = generateRequestUrl(AUTH_SERVER_GRANT_URL, requestUrlMap);
        log.info(String.format("Sending POST request to: [%s] with body: [%s]", requestUrl, userIdList));
        ResponseEntity<String> response = RestTemplateUtils.sendPostRequestWithBody(this.restTemplate, requestUrl,
                httpHeaders, userIdList);
        return RestTemplateUtils.checkResponse(response);
    }

    @Override
    public CommonResponseDto revokeRoleFromUsers(String token, String roleId, List<Object> requestObject) {
        HttpHeaders httpHeaders = createHeaderWithToken(token);
        Map<String, String> requestUrlMap = new HashMap<>();
        requestUrlMap.put(GATEWAY_PLACE_HOLDER, applicationProperties.getGatewayUrl());
        requestUrlMap.put(ROLE_ID_PLACE_HOLDER, String.valueOf(roleId));
        String requestUrl = generateRequestUrl(AUTH_SERVER_REVOKE_URL, requestUrlMap);
        log.info(String.format("Sending DELETE request to: [%s] with body: [%s]", requestUrl, requestObject));
        ResponseEntity<String> response = RestTemplateUtils.sendDeleteWithBody(this.restTemplate, requestUrl,
                httpHeaders, requestObject);
        return RestTemplateUtils.checkResponse(response);
    }

    @Override
    public List<RoleDto> getRoleListByUserName(String token, String username) {
        CommonResponseDto rolesByUserName = getRolesByUserName(token, username);
        return extractRoleDtoListFromJsonResponse(rolesByUserName);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> dtoToMap(Object dtoObject) throws WecubeCoreException {
        Map<String, Object> resultMap = new LinkedHashMap<>();
        String dtoJsonString = JsonUtils.toJsonString(dtoObject);
        try {
            resultMap = JsonUtils.toObject(dtoJsonString, resultMap.getClass());
        } catch (IOException ex) {
            String msg = String.format("Cannot transfer response's data [%s] to RoleId dto", dtoObject.toString());
            log.error(msg);
            throw new WecubeCoreException(msg);
        }
        return resultMap;
    }

    private String generateRequestUrl(String requestUrl, Map<String, String> placeHolderToParamMap) {
        Map<String, String> requestUrlParamMap = new HashMap<>();
        for (Map.Entry<String, String> entry : placeHolderToParamMap.entrySet()) {
            requestUrlParamMap.put(entry.getKey(), entry.getValue());
        }
        UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromUriString(requestUrl);
        UriComponents uriComponents = uriComponentsBuilder.buildAndExpand(requestUrlParamMap);
        return uriComponents.toString();
    }

    private HttpHeaders createHeaderWithToken(String token) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add("Authorization", token);
        return httpHeaders;
    }

    private List<RoleDto> extractRoleDtoListFromJsonResponse(CommonResponseDto requireRolesFromUserName)
            throws WecubeCoreException {
        String dataJsonString = JsonUtils.toJsonString(requireRolesFromUserName.getData());
        List<RoleDto> roleDtoList;
        try {
            roleDtoList = JsonUtils.toList(dataJsonString, RoleDto.class);
        } catch (IOException e) {
            String msg = "Cannot extract roles from the given json response";
            this.log.error(msg, dataJsonString);
            throw new WecubeCoreException(msg);
        }
        return roleDtoList;

    }
}
