jQuery(function($) {
  // idempotent redirect to HTTPS
  if (!/^https/.test(window.location.protocol)) {
    console.log('insecure');
    // window.location.replace(location.href.replace(/^http:/, "https:"))
  }

  $('.modal').modal();
});

// extensions to the security groups page
$(document).ready(() => {
  $('.js-sg-details').hover(
    function() {
      $(this)
        .find('.collapsible-header')
        .click();
    },
    function() {
      $(this)
        .find('.collapsible-header')
        .click();
    }
  );
});
