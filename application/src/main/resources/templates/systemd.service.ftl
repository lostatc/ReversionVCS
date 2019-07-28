[Unit]
Description=${description}

[Service]
Type=simple
ExecStart=${executable} %i
Restart=always
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=default.target
