package com.jasper.scheduler.controller;

import com.jasper.scheduler.dto.ApiResponse;
import com.jasper.scheduler.dto.ExternalUserDto;
import com.jasper.scheduler.mapper.ExternalUserMapper;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/users/external")
public class ExternalUserController {

    private final ExternalUserMapper mapper;

    public ExternalUserController(ExternalUserMapper mapper) {
        this.mapper = mapper;
    }

    @GetMapping
    public String list(Model model,
                       @RequestParam(required = false) String company,
                       @RequestParam(required = false) String partnerCode,
                       @RequestParam(required = false) String keyword) {
        model.addAttribute("users", mapper.findByCondition(company, partnerCode, keyword));
        model.addAttribute("company", company);
        model.addAttribute("partnerCode", partnerCode);
        model.addAttribute("keyword", keyword);
        return "user/external-list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("user", new ExternalUserDto());
        model.addAttribute("mode", "create");
        return "user/external-form";
    }

    @GetMapping("/{email}")
    @ResponseBody
    public ExternalUserDto getOne(@PathVariable String email) {
        return mapper.findByEmail(email);
    }

    @PostMapping
    @ResponseBody
    public ApiResponse<Void> create(@RequestBody ExternalUserDto dto) {
        try {
            mapper.insert(dto);
            return ApiResponse.ok("외부 사용자가 등록되었습니다.");
        } catch (Exception e) {
            return ApiResponse.fail("등록 실패: " + e.getMessage());
        }
    }

    @PutMapping("/{email}")
    @ResponseBody
    public ApiResponse<Void> update(@PathVariable String email,
                                    @RequestBody ExternalUserDto dto) {
        try {
            dto.setContactEmail(email);
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
