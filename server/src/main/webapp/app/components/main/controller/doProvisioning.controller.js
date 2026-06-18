angular.module('headwind-kiosk')
    .controller('DoProvisioningController', function ($scope, $window, $http) {
        $scope.formData = {
            apkUrl: ''
        };
        $scope.qrCodeUrl = null;
        $scope.jsonData = null;
        $scope.loading = false;
        $scope.qrSize = Math.min($window.innerWidth * 0.6, 400).toFixed(0);

        $scope.generate = function () {
            var url = ($scope.formData.apkUrl || '').trim();
            if (!url) return;

            $scope.loading = true;
            $scope.jsonData = null;

            // QR görsel URL'si (img src olarak kullanılacak)
            $scope.qrCodeUrl = 'rest/public/qr/do-provisioning?size=' +
                $scope.qrSize + '&apkUrl=' + encodeURIComponent(url);

            // JSON'u da çek
            $http.get('rest/public/qr/do-provisioning/json?apkUrl=' + encodeURIComponent(url))
                .then(function (response) {
                    if (response.status === 200) {
                        $scope.jsonData = typeof response.data === 'string'
                            ? response.data
                            : JSON.stringify(response.data, null, 2);
                    }
                    $scope.loading = false;
                }, function () {
                    $scope.loading = false;
                });
        };
    });
