//package com.opicnic.opicnic.controller;
//
//import com.opicnic.opicnic.service.STTService;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//import org.springframework.web.multipart.MultipartFile;
//
//@RestController
//@RequestMapping("/stt")
//@RequiredArgsConstructor
//@Slf4j
//public class STTController {
//    private final STTService sttService;
//
//    @PostMapping(value = "/upload", consumes = "multipart/form-data")
//    public ResponseEntity<String> uploadAudio(@RequestParam("file") MultipartFile file) {
//        log.info("STT 요청 받음: {}", file.getOriginalFilename());
//
//        String result = sttService.sendAudioToStt(file);
//        log.info("STT 변환 결과: {}", result);
//        return ResponseEntity.ok(result);
//    }
//
//
//}