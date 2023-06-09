package com.spring.blog.controller;

import com.spring.blog.entity.User;
import com.spring.blog.payload.SuccessResponse;
import com.spring.blog.payload.request.FindByPasswordRequestDto;
import com.spring.blog.payload.request.FindByUpdatePasswordRequestDto;
import com.spring.blog.payload.request.JoinUserRequestDto;
import com.spring.blog.payload.request.LoginRequestDto;
import com.spring.blog.payload.response.UserResponse;
import com.spring.blog.security.JwtTokenProvider;
import com.spring.blog.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManagerBuilder authenticationManagerBuilder;

    @PostMapping("/join")
    public ResponseEntity<User> joinUser(@Valid @RequestBody JoinUserRequestDto dto) {

        User newUser = userService.joinUser(dto);

        return new ResponseEntity<>(newUser, HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<SuccessResponse<UserResponse>> login(@Valid @RequestBody LoginRequestDto loginDto, HttpServletResponse response) {

        userService.longinUser(loginDto);

        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(loginDto.getEmail(), loginDto.getPassword());

        Authentication authentication = authenticationManagerBuilder.getObject().authenticate(authenticationToken);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        String token = jwtTokenProvider.generateToken(authentication);

        Cookie myCookie = new Cookie("cookie", token);

        myCookie.setHttpOnly(true);
        myCookie.setMaxAge(300);
        response.addCookie(myCookie);

        HttpHeaders httpHeaders = new HttpHeaders();

        return SuccessResponse.successResponseEntity(UserResponse.builder()
                .email(authentication.getName())
                .token(token)
                .build(), httpHeaders);
    }

    @GetMapping("/logout")
    @ResponseStatus(value = HttpStatus.OK)
    public ResponseEntity<SuccessResponse<String>> logout() {
        HttpHeaders httpHeaders = new HttpHeaders();

        SuccessResponse<String> successResponse = SuccessResponse.success(null);

        return new ResponseEntity<>(successResponse, httpHeaders, HttpStatus.OK);
    }

    @PostMapping("/find-password")
    @ResponseStatus(value = HttpStatus.OK)
    public void findPassword(@Valid @RequestBody FindByPasswordRequestDto dto) {
        userService.findByUserPassword(dto);
    }

    @PutMapping("/find-password/{token}")
    @ResponseStatus(value = HttpStatus.OK)
    public ResponseEntity<User> findPasswordByUpdate(@RequestParam(value = "token") String token,
                                                     @Valid @RequestBody FindByUpdatePasswordRequestDto dto) {

        User findPasswordByUpdate = userService.findUserByPassword(token, dto);

        return new ResponseEntity<>(findPasswordByUpdate, HttpStatus.OK);
    }

}
