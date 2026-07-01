package com.jasper.scheduler.controller;

import com.jasper.scheduler.dto.ApiResponse;
import com.jasper.scheduler.dto.ParamDefDto;
import com.jasper.scheduler.dto.ReportDto;
import com.jasper.scheduler.mapper.ReportMapper;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/reports")
public class ReportController {

    private final ReportMapper mapper;

    public ReportController(ReportMapper mapper) {
        this.mapper = mapper;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("reports", mapper.findAll());
        return "report/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("report", new ReportDto());
        model.addAttribute("mode", "create");
        return "report/form";
    }

    @GetMapping("/{reportId}")
    public String editForm(@PathVariable Long reportId, Model model) {
        ReportDto report = mapper.findById(reportId);
        report.setParams(mapper.findParams(reportId));
        model.addAttribute("report", report);
        model.addAttribute("mode", "edit");
        return "report/form";
    }

    @PostMapping
    @ResponseBody
    public ApiResponse<Long> create(@RequestBody ReportDto dto) {
        try {
            mapper.insert(dto);
            if (dto.getParams() != null) {
                for (ParamDefDto p : dto.getParams()) {
                    p.setReportId(dto.getReportId());
                    mapper.insertParam(p);
                }
            }
            return ApiResponse.ok(dto.getReportId());
        } catch (Exception e) {
            return ApiResponse.fail("등록 실패: " + e.getMessage());
        }
    }

    @PutMapping("/{reportId}")
    @ResponseBody
    public ApiResponse<Void> update(@PathVariable Long reportId,
                                    @RequestBody ReportDto dto) {
        try {
            dto.setReportId(reportId);
            mapper.update(dto);
            mapper.deleteParamsByReportId(reportId);
            if (dto.getParams() != null) {
                for (ParamDefDto p : dto.getParams()) {
                    p.setReportId(reportId);
                    mapper.insertParam(p);
                }
            }
            return ApiResponse.ok("수정되었습니다.");
        } catch (Exception e) {
            return ApiResponse.fail("수정 실패: " + e.getMessage());
        }
    }

    @DeleteMapping("/{reportId}")
    @ResponseBody
    public ApiResponse<Void> delete(@PathVariable Long reportId) {
        try {
            mapper.deleteParamsByReportId(reportId);
            mapper.delete(reportId);
            return ApiResponse.ok("삭제되었습니다.");
        } catch (Exception e) {
            return ApiResponse.fail("삭제 실패: " + e.getMessage());
        }
    }
}
