(function () {
    const params = new URLSearchParams(window.location.search);

    // Already has the param? Don't redirect again.
    if (params.has('loc')) return;

    if (!navigator.geolocation) {
        console.warn('Geolocation not supported');
        return;
    }

    navigator.geolocation.getCurrentPosition(
        (position) => {
            const { latitude, longitude } = position.coords;
            params.set('loc', `${latitude.toFixed(4)},${longitude.toFixed(4)}`);
            window.location.href = `${window.location.pathname}?${params.toString()}${window.location.hash}`;
        },
        (error) => {
            console.warn('Geolocation error:', error.message);
            // Optionally fall back or just do nothing
        }
    );
})();