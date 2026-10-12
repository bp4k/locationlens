package net.bp4k.locationlens.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import net.bp4k.locationlens.dto.CriteriaRequest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;



@RestController 
@RequestMapping("api/v1/place")
public class PlaceController {
    // Will implement after the push
    
    @GetMapping("/search")
    @ResponseStatus(HTTPStatus.BAD_REQUEST)
    public String search(@RequestParam @Valid CriteriaRequest param) {
        return new String();
    }

    
    @GetMapping("/{placeId}")
    @ResponseStatus(HTTPStatus.BAD_REQUEST)
    public String getPlaceDetails(@PathVariable("placeId") String placeId) {
        return new String();
    }
}
