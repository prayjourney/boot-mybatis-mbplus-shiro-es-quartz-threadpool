package com.zgy.learn.webtoken.config;

import com.zgy.learn.webtoken.pojo.OpUser;
import com.zgy.learn.webtoken.pojo.RoleAuthority;
import com.zgy.learn.webtoken.pojo.UserRole;
import com.zgy.learn.webtoken.service.AuthorityService;
import com.zgy.learn.webtoken.service.OpUserService;
import com.zgy.learn.webtoken.service.RoleAuthorityService;
import com.zgy.learn.webtoken.service.RoleService;
import com.zgy.learn.webtoken.service.UserRoleService;
import com.zgy.learn.webtoken.util.JwtTokenUtil;
import com.zgy.learn.webtoken.util.TokenConstant;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authc.UnknownAccountException;
import org.apache.shiro.authz.AuthorizationException;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.crypto.hash.SimpleHash;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import javax.crypto.SecretKey;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author renjiaxin
 * @date 2021/2/4
 */
@Slf4j
@Component
public class JwtTokenRealm extends AuthorizingRealm {
    @Resource
    JwtTokenUtil jwtTokenUtil;

    @Autowired
    OpUserService opUserService;

    @Autowired
    UserRoleService userRoleService;

    @Autowired
    RoleService roleService;

    @Autowired
    RoleAuthorityService roleAuthorityService;

    @Autowired
    AuthorityService authorityService;

    /**
     * 重写support,用来表示这个realm专门验证JwtToken, 不浮躁其他的token的验证(如: UsernamePasswordToken)
     */
    @Override
    public boolean supports(AuthenticationToken token) {
        return token instanceof JwtToken;
    }

    // 认证
    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) throws AuthenticationException {
        log.info("开始执行认证...");
        String jwt = (String) token.getCredentials();
        if (StringUtils.isEmpty(jwt)) {
            throw new UnknownAccountException();
        }

        // 这个user的真实性在登录的验证条件之中已经验证,
        Claims claims = jwtTokenUtil.parseToken(jwt);
        // 获取了用户id, 从数据库之中获取用户信息, 生成token, 和之前使用用户名密码生成的token对比
        Integer userId = Integer.parseInt(claims.getSubject());
        OpUser opUser = opUserService.queryById(userId);
        String jwtToken = jwtTokenUtil.tokenAuthentication(opUser);

        // 构造认证信息
        SimpleAuthenticationInfo info = new SimpleAuthenticationInfo(jwtToken, jwtToken, getName());
        return info;
    }


    // 授权
    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
        log.info("开始执行授权...");
        if (null == principals) {
            throw new AuthorizationException("当前用户信息为空, 请检查!");
        }
        // 获取用户名, 用户唯一标识
        OpUser opUser = (OpUser) principals.getPrimaryPrincipal();

        // 从数据库之中查询权限信息
        SimpleAuthorizationInfo authorizationInfo = new SimpleAuthorizationInfo();

        // 设置权限和角色, 用户->角色->权限, 用户->角色(多), 角色->权限(多)
        List<UserRole> userRoles = userRoleService.queryAllById(opUser.getId());
        List<String> userRolesNames = userRoles.stream().map(userRole -> {
            Integer roleId = userRole.getRoleId();
            return roleService.queryById(roleId).getName();
        }).collect(Collectors.toList());

        // 权限的id
        List<List<Integer>> authoritiesListIds = userRoles.stream().map(userRole -> {
            Integer roleId = userRole.getRoleId();
            List<RoleAuthority> roleAuthorities = roleAuthorityService.queryAllById(roleId);
            List<Integer> roleAuthoritiesIds = roleAuthorities.stream().map(RoleAuthority::getAuthorityId).collect(Collectors.toList());
            return roleAuthoritiesIds;
        }).collect(Collectors.toList());

        List<Integer> authoritiesIds = new ArrayList<>();
        for (List<Integer> ls : authoritiesListIds) {
            authoritiesIds.addAll(ls);
        }

        // 去重, 然后获取权限名
        List<String> userRolesAuthorities = authoritiesIds.stream().distinct().map(authorityId -> {
            return authorityService.queryById(authorityId).getName();
        }).collect(Collectors.toList());

        authorizationInfo.addRoles(userRolesNames);
        authorizationInfo.addStringPermissions(userRolesAuthorities);
        return authorizationInfo;
    }


//    @Override
//    public void setCredentialsMatcher(CredentialsMatcher credentialsMatcher) {
//        // 加密算法, 加密次数
//        HashedCredentialsMatcher master = new HashedCredentialsMatcher();
//        master.setHashAlgorithmName("SHA-256");
//        master.setHashIterations(1024);
//        // 设置密码匹配器
//        super.setCredentialsMatcher(master);
//    }

}