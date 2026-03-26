package com.project.safetyFence.link;

import com.project.safetyFence.link.dto.LinkRequestDto;
import com.project.safetyFence.link.dto.SupporterResponseDto;
import com.project.safetyFence.mypage.dto.NumberRequestDto;
import com.project.safetyFence.link.dto.LinkResponseDto;
import com.project.safetyFence.link.LinkService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class LinkController {

    private final LinkService linkService;

    @GetMapping("/link/list")
    public ResponseEntity<List<LinkResponseDto>> getUserLink(HttpServletRequest request) {
        String userNumber = (String) request.getAttribute("userNumber");
        List<LinkResponseDto> links = linkService.getUserLink(userNumber);
        return ResponseEntity.ok(links);
    }

    @PostMapping("/link/addUser")
    public ResponseEntity<String> addLinkUser(HttpServletRequest request, @RequestBody LinkRequestDto linkRequestDto) {
        String userNumber = (String) request.getAttribute("userNumber");
        linkService.addLinkUser(userNumber, linkRequestDto);
        return ResponseEntity.ok("Link user added successfully");
    }

    @DeleteMapping("/link/deleteUser")
    public ResponseEntity<String> deleteLinkUser(HttpServletRequest request, @RequestBody NumberRequestDto numberRequestDto) {
        String userNumber = (String) request.getAttribute("userNumber");
        String linkUserNumber = numberRequestDto.getNumber();
        linkService.deleteLinkUser(userNumber, linkUserNumber);
        return ResponseEntity.ok("Link user deleted successfully");
    }

    @GetMapping("/api/links/my-supporters")
    public ResponseEntity<List<SupporterResponseDto>> getMySupporters(HttpServletRequest request) {
        String userNumber = (String) request.getAttribute("userNumber");
        List<SupporterResponseDto> supporters = linkService.getMySupporters(userNumber);
        return ResponseEntity.ok(supporters);
    }

    @PatchMapping("/api/links/{linkId}/primary")
    public ResponseEntity<String> setPrimarySupporter(
            HttpServletRequest request,
            @PathVariable Long linkId) {
        String userNumber = (String) request.getAttribute("userNumber");
        linkService.setPrimarySupporter(userNumber, linkId);
        return ResponseEntity.ok("Primary supporter updated successfully");
    }

    @GetMapping("/api/links/primary-supporter")
    public ResponseEntity<SupporterResponseDto> getPrimarySupporter(HttpServletRequest request) {
        String userNumber = (String) request.getAttribute("userNumber");
        SupporterResponseDto primarySupporter = linkService.getPrimarySupporter(userNumber);
        return ResponseEntity.ok(primarySupporter);
    }

}
