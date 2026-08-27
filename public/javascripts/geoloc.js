document.addEventListener('DOMContentLoaded', function () {
    console.log('[geoloc] DOM loaded');

    const locationLinkA = document.getElementById('use-my-location-a');
    const locationLinkB = document.getElementById('use-my-location-b');

    console.log('[geoloc] Location link A:', locationLinkA);
    console.log('[geoloc] Location link B:', locationLinkB);

    if (!locationLinkA) {
        console.warn('[geoloc] #use-my-location-a was not found');
        return;
    }

    if (!locationLinkB) {
        console.warn('[geoloc] #use-my-location-b was not found');
        return;
    }

    if (!navigator.geolocation) {
        console.warn('[geoloc] Geolocation is not supported');
        return;
    }

    const params = new URLSearchParams(window.location.search);

    console.log('[geoloc] Current URL:', window.location.href);

    if (params.has('loc')) {
        console.log('[geoloc] loc already exists:', params.get('loc'));
        locationLinkA.remove();
        locationLinkB.remove();
        return;
    }

    function useLocation() {
        console.log('[geoloc] Requesting current position...');

        navigator.geolocation.getCurrentPosition(
            (position) => {
                console.log('[geoloc] Geolocation success');

                const { latitude, longitude } = position.coords;

                const loc = `${latitude.toFixed(4)},${longitude.toFixed(4)}`;

                console.log('[geoloc] Location:', loc);

                params.set('loc', loc);

                const newUrl =
                    `${window.location.pathname}?${params.toString()}${window.location.hash}`;

                console.log('[geoloc] Redirecting to:', newUrl);

                window.location.href = newUrl;
            },
            (error) => {
                console.warn(
                    '[geoloc] Geolocation error:',
                    error.code,
                    error.message
                );
            }
        );
    }

    function handlePermission(permission) {
        console.log(
            '[geoloc] Permission state:',
            permission.state
        );

        if (permission.state === 'granted') {
            console.log(
                '[geoloc] Permission granted - hiding link and getting location'
            );

            locationLinkA.hidden = true;
            locationLinkB.hidden = true;
            useLocation();

        } else {
            console.log(
                '[geoloc] Permission is',
                permission.state,
                '- showing link'
            );

            locationLinkA.hidden = false;
            locationLinkB.hidden = false;
        }
    }

    if (navigator.permissions) {
        console.log('[geoloc] Querying permission...');

        navigator.permissions
            .query({ name: 'geolocation' })
            .then((permission) => {
                console.log(
                    '[geoloc] Permission query result:',
                    permission.state
                );

                handlePermission(permission);

                permission.addEventListener('change', () => {
                    console.log(
                        '[geoloc] Permission changed:',
                        permission.state
                    );

                    handlePermission(permission);
                });
            })
            .catch((error) => {
                console.warn(
                    '[geoloc] Permissions API failed:',
                    error
                );

                locationLinkA.hidden = false;
                locationLinkB.hidden = false;
            });

    } else {
        console.warn(
            '[geoloc] Permissions API unavailable'
        );

        locationLinkA.hidden = false;
        locationLinkB.hidden = false;
    }

    locationLinkA.addEventListener('click', function (event) {
        console.log('[geoloc] Location link clicked');
        event.preventDefault();
        useLocation();
    });

    locationLinkB.addEventListener('click', function (event) {
        console.log('[geoloc] Location link clicked');
        event.preventDefault();
        useLocation();
    });

    console.log('[geoloc] Initialisation complete');
});
