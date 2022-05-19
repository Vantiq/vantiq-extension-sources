# syntax=docker/dockerfile:1

FROM python:3.8.13-slim-buster
RUN mkdir /app
ENV PYTHONPATH /app
RUN pip install --no-cache-dir --upgrade pip
# The localRequirements.txt file is a requirements.txt formatted file specfiying any local package installations
# desired.  This may be necessary if, for example, the python code this connector may execute requires additional
# python packages installed (e.g., tensorflow).
#
# If no additional packages are required, this file should be empty
COPY localRequirements.txt /app
WORKDIR /app
RUN pip install --no-cache-dir vantiqPythonExecConnector==${pyExecSrcVersion} &&  \
    pip install --no-cache-dir -r localRequirements.txt
ENTRYPOINT ["vantiqPythonExecConnector"]