package com.jasper.scheduler.controller;

import com.jasper.scheduler.dto.ApiResponse;
import com.jasper.scheduler.dto.InternalUserDto;
import com.jasper.scheduler.mapper.InternalUserMapper;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/users/internal")
public class InternalUserController {

    private final InternalUserMapper mapper;

    public InternalUserController(InternalUserMapper mapper) {
        this.mapper = mapper;
    }

    @GetMapping
    public String list(Model model,
                       @RequestParam(required = false) String company,
                       @RequestParam(required = false) String role,
                       @RequestParam(required = false) String keyword) {
        model.addAttribute("users", mapper.findByCondition(company, role, keyword));
        model.addAttribute("company", company);
        model.addAttribute("role", role);
        model.addAttribute("keyword", keyword);
        return "user/internal-list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("user", new InternalUserDto());
        model.addAttribute("mode", "create");
        return "user/internal-form";
    }

    // 한 이메일이 가진 모든 role/company 행을 반환 (수정 모달에서 선택)
    @GetMapping("/{email}")
    @ResponseBody
    public List<InternalUserDto> getByEmail(@PathVariable String email) {
        return mapper.findByEmail(email);
    }

    @PostMapping
    @ResponseBody
    public ApiResponse<Void> create(@RequestBody InternalUserDto dto) {
        try {
            mapper.insert(dto);
            return ApiResponse.ok("내부 사용자가 등록되었습니다.");
        } catch (Exception e) {
            return ApiResponse.fail("등록 실패: " + e.getMessage());
        }
    }

    // 복합키(email,company,role) 전체를 Body로 받아 해당 행을 정확히 수정
    // dto.origEmail/origCompany/origRole = 수정 전 값, dto.email/company/role = 수정 후 값
    @PutMapping("/row")
    @ResponseBody
    public ApiResponse<Void> updateRow(@RequestBody InternalUserDto dto) {
        try {
            boolean keyChanged =
                !dto.getOrigCompany().equals(dto.getCompany()) ||
                !dto.getOrigRole().equals(dto.getRole()) ||
                !dto.getOrigEmail().equals(dto.getEmail());

            if (keyChanged) {
                // PK 변경 = delete 후 insert
                mapper.delete(dto.getOrigEmail(), dto.getOrigCompany(), dto.getOrigRole());
                mapper.insert(dto);
            } else {
                mapper.update(dto);
            }
            return ApiResponse.ok("수정되었습니다.");
        } catch (Exception e) {
            return ApiResponse.fail("수정 실패: " + e.getMessage());
        }
    }

    // 특정 (email, company, role) 행 하나만 삭제
    @DeleteMapping("/row")
    @ResponseBody
    public ApiResponse<Void> deleteRow(@RequestParam String email,
                                       @RequestParam String company,
                                       @RequestParam String role) {
        try {
            mapper.delete(email, company, role);
            return ApiResponse.ok("삭제되었습니다.");
        } catch (Exception e) {
            return ApiResponse.fail("삭제 실패: " + e.getMessage());
        }
    }

    // 한 사람의 모든 role 행을 통째로 삭제
    @DeleteMapping("/{email}")
    @ResponseBody
    public ApiResponse<Void> deleteAll(@PathVariable String email) {
        try {
            mapper.deleteAllByEmail(email);
            return ApiResponse.ok("해당 사용자의 모든 권한이 삭제되었습니다.");
        } catch (Exception e) {
            return ApiResponse.fail("삭제 실패: " + e.getMessage());
        }
    }
}
