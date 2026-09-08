package com.rastudio.commerce.security;
import io.jsonwebtoken.Claims; import jakarta.servlet.*; import jakarta.servlet.http.*; import java.io.IOException; import org.springframework.security.authentication.UsernamePasswordAuthenticationToken; import org.springframework.security.core.authority.SimpleGrantedAuthority; import org.springframework.security.core.context.SecurityContextHolder; import org.springframework.stereotype.Component; import org.springframework.web.filter.OncePerRequestFilter; import com.rastudio.commerce.user.UserRole;
@Component public class JwtAuthenticationFilter extends OncePerRequestFilter {
 private final JwtService jwt;
 public JwtAuthenticationFilter(JwtService jwt){this.jwt=jwt;}
 @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException {
  String header=req.getHeader("Authorization");
  if(header!=null&&header.startsWith("Bearer ")) try {
   Claims claims=jwt.claims(header.substring(7));
   if("access".equals(claims.get("type",String.class))) {
    var principal=new TenantPrincipal(Long.valueOf(claims.getSubject()),claims.get("organizationId",Long.class),UserRole.valueOf(claims.get("role",String.class)));
    var authentication=new UsernamePasswordAuthenticationToken(principal,null,java.util.List.of(new SimpleGrantedAuthority("ROLE_"+principal.role().name())));
    SecurityContextHolder.getContext().setAuthentication(authentication);
   }
  } catch(Exception ignored) {}
  chain.doFilter(req,res);
 }
}
