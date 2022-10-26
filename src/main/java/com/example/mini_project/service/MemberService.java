package com.example.mini_project.service;

import com.example.mini_project.dto.TokenDto;
import com.example.mini_project.dto.requestDto.CheckIdRequestDto;
import com.example.mini_project.dto.requestDto.MemberRequestDto;
import com.example.mini_project.dto.responseDto.ResponseDto;
import com.example.mini_project.entity.Authority;
import com.example.mini_project.entity.Member;
import com.example.mini_project.entity.RefreshToken;
import com.example.mini_project.repository.MemberRepository;
import com.example.mini_project.repository.RefreshTokenRepository;
import com.example.mini_project.security.JwtFilter;
import com.example.mini_project.security.TokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.transaction.Transactional;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final AuthenticationManagerBuilder authenticationManagerBuilder;

    private final MemberRepository memberRepository;

    private final RefreshTokenRepository refreshTokenRepository;

    private final TokenProvider tokenProvider;

    private final PasswordEncoder passwordEncoder;

    @Transactional
    public ResponseDto<?> signup(MemberRequestDto memberRequestDto) {

        if (memberRepository.existsByName(memberRequestDto.getName()))
            throw new RuntimeException("중복된 닉네임입니다.");

        Member member = Member.builder()
                .name(memberRequestDto.getName())
                .password(passwordEncoder.encode(memberRequestDto.getPassword()))
                .authority(Authority.ROLE_USER)
                .build();

        memberRepository.save(member);

        return ResponseDto.success("회원가입 성공");
    }

    @Transactional
    public ResponseDto<?> login(MemberRequestDto memberRequestDto, HttpServletResponse response) {

        UsernamePasswordAuthenticationToken authenticationToken = memberRequestDto.toAuthentication();

        Authentication authentication = authenticationManagerBuilder.getObject().authenticate(authenticationToken);

        Member member = memberRepository.findByName(authentication.getName())
                .orElseThrow(() -> new UsernameNotFoundException("사용자 정보를 찾을 수 없습니다."));

        if(!passwordEncoder.matches(memberRequestDto.getPassword(), member.getPassword()))
            throw new RuntimeException("패스워드가 일치하지 않습니다.");

        TokenDto tokenDto = tokenProvider.generateTokenDto(authentication);

        RefreshToken refreshToken = RefreshToken.builder()
                .key(authentication.getName())
                .value(tokenDto.getRefreshToken())
                .build();

        refreshTokenRepository.save(refreshToken);

        response.setHeader(JwtFilter.AUTHORIZATION_HEADER, JwtFilter.BEARER_PREFIX + tokenDto.getAccessToken());
        response.setHeader("RefreshToken", tokenDto.getRefreshToken());

        return ResponseDto.success("로그인 완료");
    }

    @Transactional
    public ResponseDto<?> reissue(HttpServletRequest request, HttpServletResponse response) {
        if (!tokenProvider.validateToken(request.getHeader("RefreshToken"))) {
            throw new RuntimeException("인증이 유효하지 않습니다.");
        }

        String bearerToken = request.getHeader("Authorization").substring(7);;
        
        Authentication authentication = tokenProvider.getAuthentication(bearerToken);

        RefreshToken refreshToken = refreshTokenRepository.findByKey(authentication.getName())
                .orElseThrow(() -> new RuntimeException("로그아웃된 사용자입니다."));

        if (!refreshToken.getValue().equals(request.getHeader("RefreshToken"))) {
            throw new RuntimeException("유저 정보가 일치하지 않습니다.");
        }

        TokenDto tokenDto = tokenProvider.generateTokenDto(authentication);
        refreshToken.updateValue(tokenDto.getRefreshToken());

        response.setHeader(JwtFilter.AUTHORIZATION_HEADER, JwtFilter.BEARER_PREFIX + tokenDto.getAccessToken());
        response.setHeader("RefreshToken", tokenDto.getRefreshToken());

        return ResponseDto.success("유저 갱신 성공");
    }

    public ResponseDto<?> checkId(CheckIdRequestDto checkIdRequestDto) {
        if (memberRepository.existsByName(checkIdRequestDto.getName())){
            throw new DuplicateKeyException("존재하는 ID 입니다.");
        }else return ResponseDto.success("사용가능한 ID 입니다.");

    }

}
