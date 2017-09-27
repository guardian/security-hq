jQuery(function($){
    // idempotent redirect to HTTPS
    if (! /^https/.test(window.location.protocol)) {
        console.log("insecure");
        // window.location.replace(location.href.replace(/^http:/, "https:"))
    }

    $('.modal').modal();
});
