package com.company.meetinghelper.venue;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/venues")
public class VenueController {
    private final VenueService venueService;

    public VenueController(VenueService venueService) {
        this.venueService = venueService;
    }

    @GetMapping
    List<VenueService.VenueSummary> list() {
        return venueService.list();
    }

    @GetMapping("/{id}")
    VenueService.VenueDetail get(@PathVariable String id) {
        return venueService.get(id);
    }

    @PostMapping
    VenueService.VenueDetail create(@Valid @RequestBody VenueService.CreateVenueRequest request) {
        return venueService.create(request);
    }
}

