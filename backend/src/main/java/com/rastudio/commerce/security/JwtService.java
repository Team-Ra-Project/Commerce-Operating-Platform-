package com.rastudio.commerce.security;
import com.rastudio.commerce.user.AppUser; import io.jsonwebtoken.*; import io.jsonwebtoken.security.Keys; import java.nio.charset.StandardCharsets; import java.time.*; import java.util.*; import javax.crypto.SecretKey; import org.springframework.beans.factory.annotation.Value; import org.springframework.stereotype.Service;
@Service public class JwtService {
 private final SecretKey key; private final long accessMinutes; private final long inviteDays;
 public JwtService(@Value("${app.jwt.secret}") String secret,@Value("${app.jwt.access-minutes}") long accessMinutes,@Value("${app.jwt.invite-days}") long inviteDays){ if(secret.length()<32) throw new IllegalStateException("JWT_SECRET must be at least 32 characters"); this.key=Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));this.accessMinutes=accessMinutes;this.inviteDays=inviteDays; }
 public String access(AppUser u){return Jwts.builder().subject(u.id.toString()).claim("organizationId",u.organizationId).claim("role",u.role.name()).claim("type","access").issuedAt(new Date()).expiration(Date.from(Instant.now().plus(Duration.ofMinutes(accessMinutes)))).signWith(key).compact();}
 public String verification(AppUser u){return Jwts.builder().subject(u.id.toString()).claim("type","verify-email").issuedAt(new Date()).expiration(Date.from(Instant.now().plus(Duration.ofHours(24)))).signWith(key).compact();}
 // Signed, stateless invitation token — same pattern as verification() above
 // (schema has no dedicated invite-token table/columns), scoped to a single
 // app_user row via its id, with a longer expiry appropriate for an email
 // someone might not open for a few days.
 public String invitation(AppUser u){return Jwts.builder().subject(u.id.toString()).claim("type","invite").claim("email",u.email).issuedAt(new Date()).expiration(Date.from(Instant.now().plus(Duration.ofDays(inviteDays)))).signWith(key).compact();}
 public Claims claims(String token){return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();}
}