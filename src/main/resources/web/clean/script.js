$( document ).ready(function() {

    window.performance.mark('rum_page_ready');

    var duration = Date.now() - window.performance.timing.fetchStart;

    $('#page_ready').text("Time to Page Ready = " + duration + "ms");


    setInterval(function () {
        var seconds = new Date().getSeconds();
        var sdegree = seconds * 6 + 270;
        seconds = ("0" + seconds).slice(-2);
        var secondSel = document.querySelector(".seconds");

        secondSel.style.transform = "rotate(" + sdegree + "deg) translate(160px) rotate(-" + sdegree + "deg)";
        secondSel.innerHTML = seconds;

        var minutes = new Date().getMinutes();
        var mdegree = minutes * 6 + 270;
        var minutesSel = document.querySelector(".minutes");
        minutes = ("0" + minutes).slice(-2);

        minutesSel.style.transform = "rotate(" + mdegree + "deg) translate(181px) rotate(-" + mdegree + "deg)";
        minutesSel.innerHTML = minutes;

        var hours = new Date().getHours();
        var hoursSel = document.querySelector(".hours");

        if (hours > 12) {
            hours = hours - 12;
        }

        hours = ("0" + hours).slice(-2);

        hoursSel.innerHTML = hours;
    }, 1000);

});