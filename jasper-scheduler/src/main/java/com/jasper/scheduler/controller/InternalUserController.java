package com.jasper.scheduler.controller;

import com.jasper.scheduler.dto.ApiResponse;
import com.jasper.scheduler.dto.InternalUserDto;
import com.jasper.scheduler.mapper.InternalUserMapper;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/{email}")
    @ResponseBody
    public InternalUserDto getOne(@PathVariable String email) {
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

    @PutMapping("/{email}")
    @ResponseBody
    public ApiResponse<Void> update(@PathVariable String email,
                                    @RequestBody InternalUserDto dto) {
        try {
            dto.setEmail(email);
            mapper.update(dto);
            return ApiResponse.ok("수정되었습니다.");
        } catch (Exception e) {
            return ApiResponse.fail("수정 실패: " + e.getMessage());
        }
    }

    @DeleteMapping("/{email}")
    @ResponseBody
    public ApiResponse<Void> delete(@PathVariable String email) {
        try {
            mapper.delete(email);
            return ApiResponse.ok("삭제되었습니다.");
        } catch (Exception e) {
            return ApiResponse.fail("삭제 실패: " + e.getMessage());
        }
    }
}
