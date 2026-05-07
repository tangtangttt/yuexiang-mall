package cn.hollis.llm.mentor.langchain4j.controller;

import cn.hollis.llm.mentor.langchain4j.service.MedicalPromptRoutingService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RequestMapping("/medical")
@RestController
public class MedicalAssistantController {

    @Autowired
    private MedicalPromptRoutingService medicalRoutingService;

    @RequestMapping("/consultation")
    public Flux<String> medicalConsultation(HttpServletResponse response, @RequestParam String question) {
        response.setCharacterEncoding("UTF-8");

        String consultationType = medicalRoutingService.determineConsultationType(question);

        if ("DOCTOR".equals(consultationType.trim())) {
            return medicalRoutingService.doctorConsultation(question);
        } else if ("PHARMACIST".equals(consultationType.trim())) {
            return medicalRoutingService.pharmacistConsultation(question);
        } else {
            return medicalRoutingService.doctorConsultation(question);
        }
    }
}
