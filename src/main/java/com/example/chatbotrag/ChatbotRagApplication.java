package com.example.chatbotrag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class ChatbotRagApplication {

	public static void main(String[] args) {
		System.out.println("[APP] ChatbotRagApplication démarre");
		SpringApplication.run(ChatbotRagApplication.class, args);
	}

    @GetMapping("/ping")
    public String ping() {
        System.out.println("[APP] /ping appelé");
        return "pong";
    }

}
