package net.bp4k.locationlens.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;


@RestController 
@RequestMapping("api/v1/place")
public class PlaceController {
    // Will implement after the push
    
    @GetMapping("/search")
    public String search(@RequestParam String param) {
        return new String();
    }

    
    @GetMapping("/{placeId}")
    public String getPlaceDetails(@PathVariable("placeId") String placeId) {
        return new String();
    }
}